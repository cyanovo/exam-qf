<template>
  <div class="sidebar-logo-container" :class="{ 'collapse': collapse }">
    <transition name="sidebarLogoFade">
      <router-link v-if="collapse" key="collapse" class="sidebar-logo-link" to="/">
        <span class="sidebar-monogram" :aria-label="title" :title="title">青</span>
      </router-link>
      <router-link v-else key="expand" class="sidebar-logo-link" to="/">
        <h1 class="sidebar-title">{{ title }}</h1>
      </router-link>
    </transition>
  </div>
</template>

<script setup>
import useSettingsStore from '@/store/modules/settings'
import variables from '@/assets/styles/variables.module.scss'

defineProps({
  collapse: {
    type: Boolean,
    required: true
  }
})

const title = import.meta.env.VITE_APP_TITLE
const settingsStore = useSettingsStore()

// 获取Logo背景色
const getLogoBackground = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-bg)'
  }
  if (settingsStore.navType == 3) {
    return variables.menuLightBg
  }
  return variables.menuLightBg
})

// 获取Logo文字颜色
const getLogoTextColor = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-logo-text)'
  }
  if (settingsStore.navType == 3) {
    return variables.menuLightText
  }
  return variables.menuLightText
})
</script>

<style lang="scss" scoped>
.sidebarLogoFade-enter-active {
  transition: opacity 1.5s;
}

.sidebarLogoFade-enter,
.sidebarLogoFade-leave-to {
  opacity: 0;
}

.sidebar-logo-container {
  position: relative;
  height: 50px;
  line-height: 50px;
  background: v-bind(getLogoBackground);
  text-align: center;
  overflow: hidden;

  & .sidebar-logo-link {
    display: flex;
    align-items: center;
    padding: 0 24px;
    height: 100%;
    width: 100%;

    & .sidebar-monogram {
      color: v-bind(getLogoTextColor);
      font-size: 18px;
      font-weight: 600;
    }

    & .sidebar-title {
      display: inline-block;
      margin: 0;
      color: v-bind(getLogoTextColor);
      font-weight: 600;
      line-height: 50px;
      font-size: 16px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-family: Avenir, Helvetica Neue, Arial, Helvetica, sans-serif;
      vertical-align: middle;
    }
  }

  &.collapse {
    .sidebar-logo-link {
      justify-content: center;
      padding: 0;
    }
  }
}
</style>
