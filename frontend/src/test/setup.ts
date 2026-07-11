import '@testing-library/jest-dom/vitest'
import { config } from '@vue/test-utils'
import ElementPlus from 'element-plus'

config.global.plugins = [ElementPlus]
