<template>
  <div :class="[classObj, { 'student-layout': isStudent }]" class="app-wrapper" :style="{ '--current-color': theme, '--current-color-light': theme + '1a', '--current-color-dark-bg': theme + '33' }">
    <div v-if="!isStudent && device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside"/>
    <sidebar v-if="!isStudent && !sidebar.hide" class="sidebar-container" />
    <div :class="{ hasTagsView: !isStudent && needTagsView, sidebarHide: sidebar.hide || isStudent }" class="main-container">
      <div :class="{ 'fixed-header': fixedHeader }">
        <navbar @setLayout="setLayout" />
        <tags-view v-if="!isStudent && needTagsView" />
      </div>
      <app-main />
      <settings v-if="!isStudent" ref="settingRef" />
    </div>
  </div>
</template>

<script setup>
import { isStudentOnly } from '@/utils/examIdentity'
import { useWindowSize } from '@vueuse/core'
import Sidebar from './components/Sidebar/index.vue'
import { AppMain, Navbar, Settings, TagsView } from './components'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import useUserStore from '@/store/modules/user'

const settingsStore = useSettingsStore()
const isStudent = computed(() => isStudentOnly(useUserStore()))
const theme = computed(() => settingsStore.theme)
const sidebar = computed(() => useAppStore().sidebar)
const device = computed(() => useAppStore().device)
const needTagsView = computed(() => settingsStore.tagsView)
const fixedHeader = computed(() => settingsStore.fixedHeader)

const classObj = computed(() => ({
  hideSidebar: !sidebar.value.opened,
  openSidebar: sidebar.value.opened,
  withoutAnimation: sidebar.value.withoutAnimation,
  mobile: device.value === 'mobile'
}))

const { width, height } = useWindowSize()
const WIDTH = 992 // refer to Bootstrap's responsive design

watch(() => device.value, () => {
  if (device.value === 'mobile' && sidebar.value.opened) {
    useAppStore().closeSideBar({ withoutAnimation: false })
  }
})

watchEffect(() => {
  if (width.value - 1 < WIDTH) {
    useAppStore().toggleDevice('mobile')
    useAppStore().closeSideBar({ withoutAnimation: true })
  } else {
    useAppStore().toggleDevice('desktop')
  }
})

function handleClickOutside() {
  useAppStore().closeSideBar({ withoutAnimation: false })
}

const settingRef = ref(null)
function setLayout() {
  settingRef.value.openSetting()
}
</script>

<style lang="scss" scoped>
@use "@/assets/styles/mixin.scss" as mix;
@use "@/assets/styles/variables.module.scss" as vars;

// 侧边栏宽度（必须与 sidebar.scss 保持一致）
$sidebar-width: vars.$base-sidebar-width;            // 200px
$sidebar-collapsed-width: 64px;                       // Element Plus el-menu 折叠宽度

.app-wrapper {
  @include mix.clearfix;
  position: relative;
  height: 100%;
  width: 100%;

  &.mobile.openSidebar {
    position: fixed;
    top: 0;
  }
}

// ==================== 主内容区偏移（防止侧边栏遮挡） ====================
.main-container {
  margin-left: $sidebar-width;
  transition: margin-left 0.28s;

  // 固定头部模式下撑满视口
  &:has(.fixed-header) {
    height: 100vh;
    overflow: hidden;
  }
}

// 侧边栏折叠时（hideSidebar）
.hideSidebar .main-container {
  margin-left: $sidebar-collapsed-width;
}

// 侧边栏完全隐藏时
.main-container.sidebarHide {
  margin-left: 0;
}

.student-layout .main-container {
  margin-left: 0;
}

// 移动端
.mobile .main-container {
  margin-left: 0;
}

.mobile.hideSidebar > .sidebar-container {
  transform: translateX(-100%);
  pointer-events: none;
}

.drawer-bg {
  background: #000;
  opacity: 0.3;
  width: 100%;
  top: 0;
  height: 100%;
  position: absolute;
  z-index: 999;
}

.mobile.openSidebar > .sidebar-container {
  width: min(260px, 85vw);
}

// ==================== 固定头部（navbar + tagsView） ====================
.fixed-header {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 9;
  width: calc(100% - #{$sidebar-width});
  transition: width 0.28s;
}

.hideSidebar .fixed-header {
  width: calc(100% - #{$sidebar-collapsed-width});
}

.sidebarHide .fixed-header {
  width: 100%;
}

.student-layout .fixed-header {
  width: 100%;
}

.mobile .fixed-header {
  width: 100%;
}
</style>
