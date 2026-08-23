package ink.garry.rd.agent.ws.adapter.knowledgebase;

import ink.garry.rd.agent.ws.adapter.config.BaseController;
import ink.garry.rd.agent.ws.application.knowledgebase.KnowledgeBaseCommandService;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbCreateParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbFileNumParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbNumParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbUpdateBasicParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbUpdateIndexConfigParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbUploadFileParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbVo;
import ink.garry.rd.agent.ws.facade.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-base/command")
public class KbCommandController extends BaseController {

    @Resource
    private KnowledgeBaseCommandService knowledgeBaseCommandService;
    @Resource
    private KbVoAssembler kbVoAssembler;

    @PostMapping("/create")
    public Result<KbVo> create(@Valid @RequestBody KbCreateParam param) {
        var dto = kbVoAssembler.toCreateDTO(param);
        dto.setWorkspaceNum(getCurrentWorkspaceNum());
        return ok(kbVoAssembler.toKbVo(knowledgeBaseCommandService.create(dto, getCurrentUserId())));
    }

    @PostMapping("/updateBasic")
    public Result<KbVo> updateBasic(@Valid @RequestBody KbUpdateBasicParam param) {
        return ok(kbVoAssembler.toKbVo(knowledgeBaseCommandService.updateBasic(
                kbVoAssembler.toUpdateBasicDTO(param), getCurrentUserId())));
    }

    @PostMapping("/updateIndexConfig")
    public Result<KbVo> updateIndexConfig(@Valid @RequestBody KbUpdateIndexConfigParam param) {
        return ok(kbVoAssembler.toKbVo(knowledgeBaseCommandService.updateIndexConfig(
                kbVoAssembler.toUpdateIndexConfigDTO(param), getCurrentUserId())));
    }

    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody KbNumParam param) {
        knowledgeBaseCommandService.delete(param.getKbNum(), getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/registerFile")
    public Result<KbVo> registerFile(@Valid @RequestBody KbUploadFileParam param) {
        return ok(kbVoAssembler.toKbVo(knowledgeBaseCommandService.registerUploadedFile(
                kbVoAssembler.toUploadDTO(param), getCurrentWorkspaceNum(), getCurrentUserId())));
    }

    @PostMapping("/deleteFile")
    public Result<Void> deleteFile(@Valid @RequestBody KbFileNumParam param) {
        knowledgeBaseCommandService.deleteFile(param.getKbNum(), param.getFileNum(), getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/reindexFile")
    public Result<Void> reindexFile(@Valid @RequestBody KbFileNumParam param) {
        knowledgeBaseCommandService.reindexFile(param.getKbNum(), param.getFileNum(), getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/reindexStaleFiles")
    public Result<Void> reindexStaleFiles(@Valid @RequestBody KbNumParam param) {
        knowledgeBaseCommandService.reindexStaleFiles(param.getKbNum(), getCurrentUserId());
        return ok(null);
    }

    @PostMapping("/reindexAll")
    public Result<Void> reindexAll(@Valid @RequestBody KbNumParam param) {
        knowledgeBaseCommandService.reindexAll(param.getKbNum(), getCurrentUserId());
        return ok(null);
    }
}
