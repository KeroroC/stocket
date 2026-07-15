import {
  Bell,
  Box,
  DocumentChecked,
  FirstAidKit,
  HomeFilled,
  Location,
  Plus,
  Setting,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'

export type NavItem = {
  to: string
  label: string
  icon: Component
  roles?: string[]
}

export type NavGroup = {
  id: string
  label: string
  items: NavItem[]
}

export const desktopNavGroups: NavGroup[] = [
  {
    id: 'overview',
    label: '概览',
    items: [{ to: '/', label: '首页', icon: HomeFilled }],
  },
  {
    id: 'inventory',
    label: '库存业务',
    items: [
      { to: '/items', label: '物品目录', icon: Box },
      { to: '/receive', label: '入库', icon: Plus, roles: ['ADMIN', 'MEMBER'] },
      { to: '/inventory', label: '库存台账', icon: Location },
      { to: '/reminders', label: '提醒中心', icon: Bell },
    ],
  },
  {
    id: 'admin',
    label: '系统管理',
    items: [
      { to: '/admin/members', label: '成员管理', icon: UserFilled, roles: ['ADMIN'] },
      { to: '/admin/invites', label: '邀请管理', icon: Plus, roles: ['ADMIN'] },
      { to: '/admin/categories', label: '分类管理', icon: Box, roles: ['ADMIN'] },
      { to: '/admin/locations', label: '位置管理', icon: HomeFilled, roles: ['ADMIN'] },
      { to: '/admin/delivery-failures', label: '通知失败', icon: Bell, roles: ['ADMIN'] },
      { to: '/admin/audit-logs', label: '审计日志', icon: DocumentChecked, roles: ['ADMIN'] },
      { to: '/admin/diagnostics', label: '系统诊断', icon: FirstAidKit, roles: ['ADMIN'] },
    ],
  },
  {
    id: 'personal',
    label: '个人',
    items: [
      { to: '/profile', label: '我的账户', icon: User },
      { to: '/notification-settings', label: '通知设置', icon: Setting },
    ],
  },
]

export function visibleNavGroups(role: string): NavGroup[] {
  return desktopNavGroups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.roles || item.roles.includes(role)),
    }))
    .filter((group) => group.items.length > 0)
}
