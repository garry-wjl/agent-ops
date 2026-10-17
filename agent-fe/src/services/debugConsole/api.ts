/**
 * 调试台专用 API（非 session CRUD）
 */
import { post } from '../request';
import type { UserInputRespondParam } from '@/types';

export const debugConsoleApi = {
  userInputRespond: (param: UserInputRespondParam) =>
    post<void>('/api/v1/debug-console/user-input/respond', param),
};
