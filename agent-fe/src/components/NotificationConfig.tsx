import {
  BellOutlined,
  CloudServerOutlined,
  FormOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
} from '@ant-design/icons';

/**
 * 通知中心配置
 */
export const noticeConfig = {
  tabs: [
    {
      key: 'alert',
      title: '告警',
      count: 4,
      list: [
        {
          id: 'alert-1',
          avatar: <BellOutlined />,
          title: '服务器 CPU 使用率超过 90%',
          description: '生产环境 prod-server-01 资源告警',
          datetime: '5 分钟前',
        },
        {
          id: 'alert-2',
          avatar: <SafetyCertificateOutlined />,
          title: 'SSL 证书即将过期',
          description: 'api.example.com 证书将于 7 天后过期',
          datetime: '1 小时前',
        },
        {
          id: 'alert-3',
          avatar: <CloudServerOutlined />,
          title: '数据库连接池告警',
          description: '连接数已达到 85%，请及时处理',
          datetime: '2 小时前',
          read: true,
        },
        {
          id: 'alert-4',
          avatar: <SettingOutlined />,
          title: '定时任务执行失败',
          description: '数据同步任务 sync_orders 执行异常',
          datetime: '3 小时前',
        },
      ],
      showClear: true,
      clearText: '全部已读',
      viewMoreText: '查看全部告警',
    },
    {
      key: 'task',
      title: '待办',
      count: 3,
      list: [
        {
          id: 'task-1',
          title: '审批：新员工入职申请',
          description: '张三的入职申请待审批，截止时间 2025-01-05',
        },
        {
          id: 'task-2',
          title: '代码评审：用户模块重构',
          description: 'PR #1234 等待你的 Code Review',
        },
        {
          id: 'task-3',
          title: '文档更新：API 接口文档',
          description: '需在本周五前完成 v2.0 接口文档更新',
        },
      ],
      showClear: false,
      showViewMore: true,
      viewMoreText: '进入任务中心',
    },
    {
      key: 'system',
      title: '系统',
      count: 2,
      list: [
        {
          id: 'sys-1',
          avatar: <TeamOutlined />,
          title: '系统维护通知',
          description: '系统将于 2025-01-06 02:00-04:00 进行升级维护',
          datetime: '昨天',
        },
        {
          id: 'sys-2',
          avatar: <FormOutlined />,
          title: '新功能上线',
          description: '数据看板 v2.0 已上线，支持自定义图表配置',
          datetime: '3 天前',
          read: true,
        },
      ],
      showClear: true,
      clearText: '清空系统通知',
      viewMoreText: '查看更多',
    },
  ],
  onTabChange: (_key: string) => {
    // TODO: 实现通知 Tab 切换逻辑
  },
  onItemClick: (_item: { title: string }, _tabKey: string) => {
    // TODO: 实现通知项点击逻辑（如标记已读、跳转详情等）
  },
  onClear: (_tabKey: string) => {
    // TODO: 实现清空通知逻辑
  },
  onViewMore: (_tabKey: string) => {
    // TODO: 实现查看更多通知逻辑（如跳转通知中心页面）
  },
};
