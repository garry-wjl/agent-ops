package ink.garry.rd.agent.ws.adapter.knowledgebase;

import ink.garry.rd.agent.ws.adapter.workspace.WorkspaceCommandController;
import ink.garry.rd.agent.ws.adapter.workspace.WorkspaceQueryController;
import ink.garry.rd.agent.ws.adapter.workspace.assembler.WorkspaceVoAssembler;
import ink.garry.rd.agent.ws.application.knowledgebase.KnowledgeBaseCommandService;
import ink.garry.rd.agent.ws.application.knowledgebase.KnowledgeBaseQueryService;
import ink.garry.rd.agent.ws.application.workspace.WorkspaceKbConfigCommandService;
import ink.garry.rd.agent.ws.application.workspace.WorkspaceKbConfigQueryService;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbChunkDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbConfigAlignmentDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbDetailDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbFileDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.KbTypeSchemaDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.dto.MountableKbItemDTO;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbCreateParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbFileNumParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbListQueryParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbNumParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbTestRetrieveParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbUpdateBasicParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbUpdateIndexConfigParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbUploadFileParam;
import ink.garry.rd.agent.ws.client.knowledgebase.vo.KbVo;
import ink.garry.rd.agent.ws.client.workspace.dto.WorkspaceKbConfigDTO;
import ink.garry.rd.agent.ws.client.workspace.vo.WorkspaceKbConfigSaveParam;
import ink.garry.rd.agent.ws.facade.common.PageVO;
import ink.garry.rd.agent.ws.facade.common.Result;
import ink.garry.rd.agent.ws.infra.common.util.UserContext;
import ink.garry.rd.agent.ws.infra.common.util.UserContextHolder;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContext;
import ink.garry.rd.agent.ws.infra.common.util.WorkspaceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库 HTTP 契约测试：覆盖技术方案 §7.2 全部路由 + 工作空间 KB 配置。
 */
@ExtendWith(MockitoExtension.class)
class KbApiContractTest {

    private static final String WS = "WS1";
    private static final String USER = "u1";

    @Mock
    private KnowledgeBaseCommandService commandService;
    @Mock
    private KnowledgeBaseQueryService queryService;
    @Mock
    private WorkspaceKbConfigCommandService workspaceKbConfigCommandService;
    @Mock
    private WorkspaceKbConfigQueryService workspaceKbConfigQueryService;

    private final KbVoAssembler kbAssembler = new KbVoAssembler();
    private final WorkspaceVoAssembler workspaceAssembler = new WorkspaceVoAssembler();
    private KbCommandController commandController;
    private KbQueryController queryController;
    private WorkspaceCommandController workspaceCommandController;
    private WorkspaceQueryController workspaceQueryController;

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder().userId(USER).build());
        WorkspaceContextHolder.set(WorkspaceContext.builder().workspaceNum(WS).member(true).build());

        commandController = new KbCommandController();
        queryController = new KbQueryController();
        workspaceCommandController = new WorkspaceCommandController();
        workspaceQueryController = new WorkspaceQueryController();

        ReflectionTestUtils.setField(commandController, "knowledgeBaseCommandService", commandService);
        ReflectionTestUtils.setField(commandController, "kbVoAssembler", kbAssembler);
        ReflectionTestUtils.setField(queryController, "knowledgeBaseQueryService", queryService);
        ReflectionTestUtils.setField(queryController, "kbVoAssembler", kbAssembler);
        ReflectionTestUtils.setField(workspaceCommandController, "workspaceKbConfigCommandService",
                workspaceKbConfigCommandService);
        ReflectionTestUtils.setField(workspaceCommandController, "assembler", workspaceAssembler);
        ReflectionTestUtils.setField(workspaceQueryController, "workspaceKbConfigQueryService",
                workspaceKbConfigQueryService);
        ReflectionTestUtils.setField(workspaceQueryController, "assembler", workspaceAssembler);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        WorkspaceContextHolder.clear();
    }

    @Test
    void command_create_injectsWorkspace() {
        when(commandService.create(any(), eq(USER)))
                .thenReturn(KbDTO.builder().kbNum("KB1").status("READY").build());

        KbCreateParam param = new KbCreateParam();
        param.setName("技术库");
        param.setKbType("SIMPLE");

        Result<KbVo> result = commandController.create(param);
        assertEquals(0, result.getCode());
        assertEquals("KB1", result.getData().getKbNum());
        verify(commandService).create(any(), eq(USER));
    }

    @Test
    void command_updateBasic() {
        when(commandService.updateBasic(any(), eq(USER)))
                .thenReturn(KbDTO.builder().kbNum("KB1").name("新名").build());
        KbUpdateBasicParam param = new KbUpdateBasicParam();
        param.setKbNum("KB1");
        param.setName("新名");
        assertEquals(0, commandController.updateBasic(param).getCode());
    }

    @Test
    void command_updateIndexConfig() {
        when(commandService.updateIndexConfig(any(), eq(USER)))
                .thenReturn(KbDTO.builder().kbNum("KB1").configVersion(2).build());
        KbUpdateIndexConfigParam param = new KbUpdateIndexConfigParam();
        param.setKbNum("KB1");
        assertEquals(0, commandController.updateIndexConfig(param).getCode());
    }

    @Test
    void command_delete() {
        KbNumParam param = new KbNumParam();
        param.setKbNum("KB1");
        assertEquals(0, commandController.delete(param).getCode());
        verify(commandService).delete("KB1", USER);
    }

    @Test
    void command_registerFile() {
        when(commandService.registerUploadedFile(any(), eq(WS), eq(USER)))
                .thenReturn(KbDTO.builder().kbNum("KB1").build());
        KbUploadFileParam param = new KbUploadFileParam();
        param.setKbNum("KB1");
        param.setOssFileId("oss-1");
        param.setFileName("a.pdf");
        assertEquals(0, commandController.registerFile(param).getCode());
    }

    @Test
    void command_fileOps() {
        KbFileNumParam fileParam = new KbFileNumParam();
        fileParam.setKbNum("KB1");
        fileParam.setFileNum("KBF1");
        assertEquals(0, commandController.deleteFile(fileParam).getCode());
        assertEquals(0, commandController.reindexFile(fileParam).getCode());
        KbNumParam kbParam = new KbNumParam();
        kbParam.setKbNum("KB1");
        assertEquals(0, commandController.reindexStaleFiles(kbParam).getCode());
        assertEquals(0, commandController.reindexAll(kbParam).getCode());
    }

    @Test
    void query_list_detail_files_mountable() {
        when(queryService.list(any(), eq(WS)))
                .thenReturn(PageVO.of(List.of(KbDTO.builder().kbNum("KB1").build()), 1L, 1, 20));
        when(queryService.detail("KB1", WS))
                .thenReturn(KbDetailDTO.builder().kbNum("KB1").status("READY").build());
        when(queryService.listFiles("KB1"))
                .thenReturn(List.of(KbFileDTO.builder().fileNum("KBF1").build()));
        when(queryService.mountable(WS))
                .thenReturn(List.of(MountableKbItemDTO.builder().kbNum("KB1").name("n").build()));

        assertEquals(0, queryController.list(new KbListQueryParam()).getCode());
        assertEquals(0, queryController.detail("KB1").getCode());
        assertEquals(0, queryController.files("KB1").getCode());
        assertEquals(0, queryController.mountable().getCode());
    }

    @Test
    void query_typeSchemas_configAlignment_testRetrieve() {
        KbTypeSchemaDTO schema = new KbTypeSchemaDTO();
        schema.setKbType("SIMPLE");
        when(queryService.typeSchemas()).thenReturn(List.of(schema));
        when(queryService.configAlignmentStats("KB1"))
                .thenReturn(List.of(KbConfigAlignmentDTO.builder().configVersion(1).fileCount(2).build()));
        when(queryService.testRetrieve(any()))
                .thenReturn(List.of(KbChunkDTO.builder().content("hit").score(0.9).build()));

        assertEquals(0, queryController.typeSchemas().getCode());
        assertEquals(0, queryController.configAlignment("KB1").getCode());
        KbTestRetrieveParam tr = new KbTestRetrieveParam();
        tr.setKbNum("KB1");
        tr.setQuestion("test");
        Result<?> retrieve = queryController.testRetrieve(tr);
        assertEquals(0, retrieve.getCode());
        assertNotNull(retrieve.getData());
    }

    @Test
    void workspace_kbConfig_getAndSave() {
        when(workspaceKbConfigQueryService.get(WS)).thenReturn(new WorkspaceKbConfigDTO());
        when(workspaceKbConfigCommandService.save(any(), eq(WS), eq(USER)))
                .thenReturn(new WorkspaceKbConfigDTO());

        assertEquals(0, workspaceQueryController.kbConfig().getCode());
        WorkspaceKbConfigSaveParam save = new WorkspaceKbConfigSaveParam();
        assertEquals(0, workspaceCommandController.saveKbConfig(save).getCode());
        verify(workspaceKbConfigCommandService).save(any(), eq(WS), eq(USER));
    }
}
