package ink.garry.rd.agent.ws.application.evaluation.dataset.casegen;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ink.garry.rd.agent.ws.application.agent.AgentQueryService;
import ink.garry.rd.agent.ws.application.debugconsole.AgentInvokeService;
import ink.garry.rd.agent.ws.application.debugconsole.SegmentAccumulator;
import ink.garry.rd.agent.ws.application.evaluation.dataset.EvalDatasetQueryService;
import ink.garry.rd.agent.ws.client.agent.dto.AgentDetailViewDTO;
import ink.garry.rd.agent.ws.client.agent.dto.AgentVersionDetailViewDTO;
import ink.garry.rd.agent.ws.client.evaluation.dataset.EvalDatasetDetailVO;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.EvalDataset;
import ink.garry.rd.agent.ws.domain.evaluation.dataset.factory.EvalDatasetFactory;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContext;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.entity.EvalDatasetCaseGenJobEntity;
import ink.garry.rd.agent.ws.infra.evaluation.dataset.mapper.EvalDatasetCaseGenJobMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 自动生成 Case 异步 Worker：调用生成器 Agent → 解析 → 写入草稿。
 */
@Slf4j
@Component
public class EvalDatasetCaseGenWorker {

    @Value("${app.evaluation.case-gen.invoke-timeout-seconds:180}")
    private int invokeTimeoutSeconds;

    @Resource
    private EvalDatasetCaseGenJobMapper caseGenJobMapper;
    @Resource
    private EvalDatasetQueryService evalDatasetQueryService;
    @Resource
    private EvalDatasetFactory evalDatasetFactory;
    @Resource
    private AgentQueryService agentQueryService;
    @Resource
    private AgentInvokeService agentInvokeService;

    @Async("evaluationExecutor")
    public void runAsync(String jobNum, String operatorId) {
        try {
            execute(jobNum, operatorId);
        } catch (Exception ex) {
            log.error("EvalDatasetCaseGenWorker failed jobNum={}", jobNum, ex);
            failJob(jobNum, operatorId, StrUtil.maxLength(ex.getMessage(), 2000));
        }
    }

    private void execute(String jobNum, String operatorId) {
        EvalDatasetCaseGenJobEntity job = require(jobNum);
        WorkspaceContextHolder.set(WorkspaceContext.builder()
                .workspaceNum(job.getWorkspaceNum())
                .role("ADMIN")
                .member(true)
                .build());
        try {
            markRunning(job, operatorId, 5, "准备提示词");
            EvalDatasetDetailVO dataset = evalDatasetQueryService.detail(job.getDatasetNum(), job.getWorkspaceNum());

            String underTestName = null;
            String underTestDesc = null;
            String underTestPrompt = null;
            if ("AGENT".equalsIgnoreCase(dataset.getType()) && StrUtil.isNotBlank(dataset.getAgentNum())) {
                try {
                    AgentDetailViewDTO under = agentQueryService.detail(dataset.getAgentNum(), job.getWorkspaceNum());
                    underTestName = under.getName();
                    underTestDesc = under.getDescription();
                    underTestPrompt = extractSystemPrompt(under);
                } catch (Exception ex) {
                    log.warn("load under-test agent failed dataset={} agent={}: {}",
                            dataset.getNum(), dataset.getAgentNum(), ex.getMessage());
                }
            }

            String prompt = CaseGenPromptBuilder.build(new CaseGenPromptBuilder.PromptInput(
                    dataset.getName(),
                    dataset.getDescription(),
                    dataset.getType(),
                    dataset.getSchemaJson(),
                    job.getTargetCount(),
                    dataset.getAgentNum(),
                    underTestName,
                    underTestDesc,
                    underTestPrompt,
                    job.getInstructionMode(),
                    job.getUserInstruction()));

            updatePromptSnapshot(jobNum, prompt, operatorId);
            markProgress(jobNum, operatorId, 20, "调用生成器 Agent");

            String raw = invokeGenerator(job, prompt, operatorId);
            updateRawOutput(jobNum, raw, operatorId);
            markProgress(jobNum, operatorId, 70, "解析输出");

            int maxWrite = job.getTargetCount() != null && job.getTargetCount() > 0
                    ? Math.min(job.getTargetCount(), EvalDatasetCaseGenCommandService.HARD_CAP)
                    : EvalDatasetCaseGenCommandService.HARD_CAP;
            CaseGenResultParser.ParseOutcome parsed = CaseGenResultParser.parse(raw, dataset.getSchemaJson(), maxWrite);

            markProgress(jobNum, operatorId, 85, "写入草稿");
            EvalDataset d = evalDatasetFactory.createByNum(job.getDatasetNum());
            if (Boolean.TRUE.equals(job.getClearDraft())) {
                d.replaceDraftRows(parsed.validDataJsonList(), operatorId);
            } else {
                for (String rowJson : parsed.validDataJsonList()) {
                    d.appendDraftRow(rowJson, operatorId);
                }
            }

            markFinished(jobNum, operatorId, parsed.parsedCount(), parsed.validDataJsonList().size(), parsed.skippedCount());
            log.info("caseGen finished jobNum={} parsed={} written={} skipped={}",
                    jobNum, parsed.parsedCount(), parsed.validDataJsonList().size(), parsed.skippedCount());
        } finally {
            WorkspaceContextHolder.clear();
        }
    }

    private String invokeGenerator(EvalDatasetCaseGenJobEntity job, String prompt, String operatorId) {
        SegmentAccumulator acc = new SegmentAccumulator();
        String version = job.getGeneratorAgentVersionNum();
        agentInvokeService.invokeStream(
                        job.getGeneratorAgentNum(),
                        prompt,
                        null,
                        null,
                        operatorId,
                        version,
                        null)
                .doOnNext(acc::accept)
                .blockLast(Duration.ofSeconds(invokeTimeoutSeconds));
        String text = acc.toContentText();
        if (StrUtil.isBlank(text)) {
            throw new IllegalStateException("生成器 Agent 未返回有效文本");
        }
        return text;
    }

    private String extractSystemPrompt(AgentDetailViewDTO under) {
        if (under.getCurrentSnapshot() != null) {
            Object sp = under.getCurrentSnapshot().get("systemPrompt");
            if (sp != null && StrUtil.isNotBlank(String.valueOf(sp))) {
                return String.valueOf(sp);
            }
        }
        AgentVersionDetailViewDTO ver = under.getCurrentVersion();
        if (ver != null && ver.getConfigSnapshot() != null) {
            Object sp = ver.getConfigSnapshot().get("systemPrompt");
            if (sp != null && StrUtil.isNotBlank(String.valueOf(sp))) {
                return String.valueOf(sp);
            }
        }
        return null;
    }

    private EvalDatasetCaseGenJobEntity require(String jobNum) {
        EvalDatasetCaseGenJobEntity e = caseGenJobMapper.selectOne(Wrappers.<EvalDatasetCaseGenJobEntity>lambdaQuery()
                .eq(EvalDatasetCaseGenJobEntity::getNum, jobNum)
                .eq(EvalDatasetCaseGenJobEntity::getDeleted, 0));
        if (e == null) {
            throw new IllegalStateException("job not found: " + jobNum);
        }
        return e;
    }

    private void markRunning(EvalDatasetCaseGenJobEntity job, String operatorId, int pct, String msg) {
        job.setStatus(EvalDatasetCaseGenCommandService.STATUS_RUNNING);
        job.setProgressPct(pct);
        job.setProgressMessage(msg);
        job.setUpdateNo(operatorId);
        job.setUpdateTime(LocalDateTime.now());
        caseGenJobMapper.updateById(job);
    }

    private void markProgress(String jobNum, String operatorId, int pct, String msg) {
        EvalDatasetCaseGenJobEntity job = require(jobNum);
        job.setProgressPct(pct);
        job.setProgressMessage(msg);
        job.setUpdateNo(operatorId);
        job.setUpdateTime(LocalDateTime.now());
        caseGenJobMapper.updateById(job);
    }

    private void updatePromptSnapshot(String jobNum, String prompt, String operatorId) {
        EvalDatasetCaseGenJobEntity job = require(jobNum);
        job.setPromptSnapshot(prompt);
        job.setUpdateNo(operatorId);
        job.setUpdateTime(LocalDateTime.now());
        caseGenJobMapper.updateById(job);
    }

    private void updateRawOutput(String jobNum, String raw, String operatorId) {
        EvalDatasetCaseGenJobEntity job = require(jobNum);
        job.setRawOutput(raw == null ? null : StrUtil.maxLength(raw, 500_000));
        job.setUpdateNo(operatorId);
        job.setUpdateTime(LocalDateTime.now());
        caseGenJobMapper.updateById(job);
    }

    private void markFinished(String jobNum, String operatorId, int parsed, int written, int skipped) {
        EvalDatasetCaseGenJobEntity job = require(jobNum);
        job.setStatus(EvalDatasetCaseGenCommandService.STATUS_FINISHED);
        job.setProgressPct(100);
        job.setProgressMessage("完成");
        job.setParsedCount(parsed);
        job.setWrittenCount(written);
        job.setSkippedCount(skipped);
        job.setErrorMessage(null);
        job.setUpdateNo(operatorId);
        job.setUpdateTime(LocalDateTime.now());
        caseGenJobMapper.updateById(job);
    }

    private void failJob(String jobNum, String operatorId, String error) {
        try {
            EvalDatasetCaseGenJobEntity job = require(jobNum);
            job.setStatus(EvalDatasetCaseGenCommandService.STATUS_FAILED);
            job.setProgressMessage("失败");
            job.setErrorMessage(error);
            job.setUpdateNo(operatorId);
            job.setUpdateTime(LocalDateTime.now());
            caseGenJobMapper.updateById(job);
        } catch (Exception ex) {
            log.warn("failJob update error jobNum={}: {}", jobNum, ex.getMessage());
        }
    }
}
