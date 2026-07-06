<script lang="ts" setup>
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalSide from "@/components/GlobalSide.vue";
</script>

<template>
  <div id="basicLayout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout>
        <GlobalSide class="side" />
        <a-layout-content class="content">
          <router-view />
        </a-layout-content>
      </a-layout>
    </a-layout>
  </div>

</template>

<style scoped>
#basicLayout{
  .header{
    padding-inline: 20px;
    background: white;
    color: unset;
    margin-bottom: 1px;
  }

  .side{
    background: #fff;
    border-right: 0.5px solid #eee;
    padding-top: 20px;
  }

  .content{
    padding: 28px;
    background: linear-gradient(to right, #fefefe, #fff);
    margin-bottom: 28px;
  }

  :deep(.ant-menu-root) {
    border-bottom: none !important;
    border-inline-end: none !important;
  }
}
</style>
