/**
 * 知识库 react-query hooks
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useWorkspaceStore } from '@/stores/workspace';
import { knowledgeBaseApi } from './api';
import type {
  KbCreateParam,
  KbFileNumParam,
  KbListQueryParam,
  KbNumParam,
  KbTestRetrieveParam,
  KbUpdateBasicParam,
  KbUpdateIndexConfigParam,
  KbUploadFileParam,
} from '@/types';

export const kbQueryKeys = {
  list: () => ['kb', 'list'] as const,
  detail: (kbNum: string) => ['kb', 'detail', kbNum] as const,
  files: (kbNum: string) => ['kb', 'files', kbNum] as const,
  mountable: () => ['kb', 'mountable'] as const,
  typeSchemas: () => ['kb', 'typeSchemas'] as const,
  configAlignment: (kbNum: string) => ['kb', 'configAlignment', kbNum] as const,
};

export function useKbListQuery(query: KbListQueryParam) {
  const ws = useWorkspaceStore((s) => s.currentWorkspaceNum);
  return useQuery({
    queryKey: [...kbQueryKeys.list(), ws, query],
    queryFn: () => knowledgeBaseApi.list(query),
    enabled: !!ws,
    staleTime: 30_000,
  });
}

export function useKbDetailQuery(kbNum: string | undefined) {
  return useQuery({
    queryKey: kbQueryKeys.detail(kbNum ?? ''),
    queryFn: () => knowledgeBaseApi.detail(kbNum as string),
    enabled: !!kbNum,
  });
}

export function useKbFilesQuery(kbNum: string | undefined) {
  return useQuery({
    queryKey: kbQueryKeys.files(kbNum ?? ''),
    queryFn: () => knowledgeBaseApi.files(kbNum as string),
    enabled: !!kbNum,
    staleTime: 15_000,
  });
}

export function useKbMountableQuery(enabled = true) {
  const ws = useWorkspaceStore((s) => s.currentWorkspaceNum);
  return useQuery({
    queryKey: [...kbQueryKeys.mountable(), ws],
    queryFn: () => knowledgeBaseApi.mountable(),
    enabled: enabled && !!ws,
    staleTime: 30_000,
  });
}

export function useKbTypeSchemasQuery() {
  return useQuery({
    queryKey: kbQueryKeys.typeSchemas(),
    queryFn: () => knowledgeBaseApi.typeSchemas(),
    staleTime: 60_000,
  });
}

export function useKbConfigAlignmentQuery(kbNum: string | undefined) {
  return useQuery({
    queryKey: kbQueryKeys.configAlignment(kbNum ?? ''),
    queryFn: () => knowledgeBaseApi.configAlignment(kbNum as string),
    enabled: !!kbNum,
  });
}

function useInvalidateList() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: kbQueryKeys.list() });
}

function useInvalidateByKbNum() {
  const qc = useQueryClient();
  return (kbNum: string) => {
    qc.invalidateQueries({ queryKey: kbQueryKeys.detail(kbNum) });
    qc.invalidateQueries({ queryKey: kbQueryKeys.files(kbNum) });
    qc.invalidateQueries({ queryKey: kbQueryKeys.configAlignment(kbNum) });
    qc.invalidateQueries({ queryKey: kbQueryKeys.mountable() });
  };
}

export function useKbCreateMutation() {
  const invalidateList = useInvalidateList();
  return useMutation({
    mutationFn: (param: KbCreateParam) => knowledgeBaseApi.create(param),
    onSuccess: () => invalidateList(),
  });
}

export function useKbUpdateBasicMutation() {
  const invalidateList = useInvalidateList();
  const invalidateByKbNum = useInvalidateByKbNum();
  return useMutation({
    mutationFn: (param: KbUpdateBasicParam) =>
      knowledgeBaseApi.updateBasic(param),
    onSuccess: (_, param) => {
      invalidateList();
      invalidateByKbNum(param.kbNum);
    },
  });
}

export function useKbUpdateIndexConfigMutation() {
  const invalidateList = useInvalidateList();
  const invalidateByKbNum = useInvalidateByKbNum();
  return useMutation({
    mutationFn: (param: KbUpdateIndexConfigParam) =>
      knowledgeBaseApi.updateIndexConfig(param),
    onSuccess: (_, param) => {
      invalidateList();
      invalidateByKbNum(param.kbNum);
    },
  });
}

export function useKbDeleteMutation() {
  const invalidateList = useInvalidateList();
  return useMutation({
    mutationFn: (param: KbNumParam) => knowledgeBaseApi.delete(param),
    onSuccess: () => invalidateList(),
  });
}

export function useKbRegisterFileMutation() {
  return useMutation({
    mutationFn: (param: KbUploadFileParam) =>
      knowledgeBaseApi.registerFile(param),
  });
}

export function useKbDeleteFileMutation() {
  return useMutation({
    mutationFn: (param: KbFileNumParam) => knowledgeBaseApi.deleteFile(param),
  });
}

export function useKbReindexFileMutation() {
  return useMutation({
    mutationFn: (param: KbFileNumParam) => knowledgeBaseApi.reindexFile(param),
  });
}

export function useKbReindexStaleMutation() {
  return useMutation({
    mutationFn: (param: KbNumParam) => knowledgeBaseApi.reindexStaleFiles(param),
  });
}

export function useKbReindexAllMutation() {
  return useMutation({
    mutationFn: (param: KbNumParam) => knowledgeBaseApi.reindexAll(param),
  });
}

export function useKbTestRetrieveMutation() {
  return useMutation({
    mutationFn: (param: KbTestRetrieveParam) =>
      knowledgeBaseApi.testRetrieve(param),
  });
}
