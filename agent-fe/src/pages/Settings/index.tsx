import { PageContainer } from '@ant-design/pro-components';
import { Result } from 'antd';

function Settings() {
  return (
    <PageContainer header={{ title: '系统设置' }}>
      <Result status='info' title='系统设置' subTitle='此功能将在 v0.2 版本中提供' />
    </PageContainer>
  );
}

export default Settings;
