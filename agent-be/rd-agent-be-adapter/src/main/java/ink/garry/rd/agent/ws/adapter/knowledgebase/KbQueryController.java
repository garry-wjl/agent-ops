package ink.garry.rd.agent.ws.adapter.knowledgebase;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.knowledgebase.KnowledgeBaseQueryService;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbChunkVo;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbDetailVo;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbFileVo;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbListQueryParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbTestRetrieveParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbTypeSchemaVo;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbVo;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.MountableKbItemVo;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge-base")
public class KbQueryController extends BaseController {

    @Resource
    private KnowledgeBaseQueryService knowledgeBaseQueryService;
    @Resource
    private KbVoAssembler kbVoAssembler;

    @GetMapping("/query/list")
    public Result<PageVO<KbVo>> list(KbListQueryParam query) {
        return ok(kbVoAssembler.toKbPageVO(knowledgeBaseQueryService.list(
                kbVoAssembler.toListDTO(query), getCurrentWorkspaceNum())));
    }

    @GetMapping("/query/detail")
    public Result<KbDetailVo> detail(@RequestParam("kbNum") String kbNum) {
        return ok(kbVoAssembler.toDetailVo(knowledgeBaseQueryService.detail(kbNum, getCurrentWorkspaceNum())));
    }

    @GetMapping("/query/files")
    public Result<List<KbFileVo>> files(@RequestParam("kbNum") String kbNum) {
        return ok(kbVoAssembler.toFileVoList(knowledgeBaseQueryService.listFiles(kbNum)));
    }

    @GetMapping("/query/mountable")
    public Result<List<MountableKbItemVo>> mountable() {
        return ok(kbVoAssembler.toMountableVoList(knowledgeBaseQueryService.mountable(getCurrentWorkspaceNum())));
    }

    @GetMapping("/query/typeSchemas")
    public Result<List<KbTypeSchemaVo>> typeSchemas() {
        return ok(kbVoAssembler.toTypeSchemaVoList(knowledgeBaseQueryService.typeSchemas()));
    }

    @GetMapping("/query/configAlignment")
    public Result<List<ink.garry.rd.agent.ws.client.knowledgebase.dto.KbConfigAlignmentDTO>> configAlignment(
            @RequestParam("kbNum") String kbNum) {
        return ok(knowledgeBaseQueryService.configAlignmentStats(kbNum));
    }

    @PostMapping("/query/testRetrieve")
    public Result<List<KbChunkVo>> testRetrieve(@Valid @RequestBody KbTestRetrieveParam param) {
        return ok(kbVoAssembler.toChunkVoList(knowledgeBaseQueryService.testRetrieve(
                kbVoAssembler.toTestRetrieveDTO(param))));
    }
}
