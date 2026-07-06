<script lang="ts" setup>
import { ref } from 'vue'
import {
  createPictureOutPaintingTaskUsingPost,
  getPictureOutPaintingTaskUsingGet,
  uploadPictureByUrlUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'

interface Props {
  picture?: API.PictureVO
  spaceId?: number
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

const resultImageUrl = ref<string>('')
const taskId = ref<string>()
let pollingTimer: ReturnType<typeof setInterval> | null = null

const clearPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
  taskId.value = undefined
}

const startPolling = () => {
  if (!taskId.value || pollingTimer) {
    return
  }

  pollingTimer = setInterval(async () => {
    try {
      const res = await getPictureOutPaintingTaskUsingGet({
        taskId: taskId.value,
      })
      if (res.data.code !== 0) {
        message.error('扩图任务执行失败：' + res.data.message)
        clearPolling()
        return
      }
      if (!res.data.data?.output) {
        return
      }

      const taskResult = res.data.data.output
      if (taskResult.taskStatus === 'SUCCEEDED') {
        if (!taskResult.outputImageUrl) {
          message.error('扩图任务执行成功，但未返回图片地址')
          clearPolling()
          return
        }
        message.success('扩图任务执行成功')
        resultImageUrl.value = taskResult.outputImageUrl
        clearPolling()
      } else if (taskResult.taskStatus === 'FAILED') {
        const failReason = taskResult.message || taskResult.code || '未知原因'
        message.error('扩图任务执行失败：' + failReason)
        clearPolling()
      }
    } catch (error: any) {
      console.error('扩图任务轮询失败', error)
      message.error('扩图任务轮询失败：' + (error?.message || '请求异常'))
      clearPolling()
    }
  }, 3000)
}

const createTask = async () => {
  if (!props.picture?.id) {
    return
  }
  try {
    const res = await createPictureOutPaintingTaskUsingPost({
      pictureId: props.picture.id,
      parameters: {
        xScale: 2,
        yScale: 2,
      },
    })
    if (res.data.code === 0 && res.data.data?.output?.taskId) {
      message.success('创建任务成功，请耐心等待，不要退出界面')
      console.log('create out painting task success', res.data.data.output.taskId)
      taskId.value = res.data.data.output.taskId
      startPolling()
    } else {
      message.error('创建扩图任务失败：' + res.data.message)
    }
  } catch (error: any) {
    console.error('创建扩图任务失败', error)
    message.error('创建扩图任务失败：' + (error?.message || '请求异常'))
  }
}

const uploadLoading = ref(false)

const handleUpload = async () => {
  uploadLoading.value = true
  try {
    const params: API.PictureUploadRequest = {
      fileUrl: resultImageUrl.value,
      spaceId: props.spaceId,
    }
    if (props.picture) {
      params.id = props.picture.id
    }
    const res = await uploadPictureByUrlUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      props.onSuccess?.(res.data.data)
      closeModal()
    } else {
      message.error('图片上传失败：' + res.data.message)
    }
  } catch (error: any) {
    console.error('图片上传失败', error)
    message.error('图片上传失败：' + (error?.message || '请求异常'))
  }
  uploadLoading.value = false
}

const visible = ref(false)

const openModal = () => {
  visible.value = true
}

const closeModal = () => {
  clearPolling()
  visible.value = false
}

defineExpose({
  openModal,
})
</script>

<template>
  <a-modal
    class="image-out-painting"
    v-model:visible="visible"
    title="AI 扩图"
    :footer="false"
    @cancel="closeModal"
  >
    <a-row gutter="16">
      <a-col span="12">
        <h4>原始图片</h4>
        <img :src="picture?.url" :alt="picture?.name" style="max-width: 100%" />
      </a-col>
      <a-col span="12">
        <h4>扩图结果</h4>
        <img
          v-if="resultImageUrl"
          :src="resultImageUrl"
          :alt="picture?.name"
          style="max-width: 100%"
        />
      </a-col>
    </a-row>
    <div style="margin-bottom: 16px" />
    <a-flex justify="center" gap="16">
      <a-button type="primary" :loading="!!taskId" ghost @click="createTask">生成图片</a-button>
      <a-button v-if="resultImageUrl" type="primary" :loading="uploadLoading" @click="handleUpload">
        应用结果
      </a-button>
    </a-flex>
  </a-modal>
</template>

<style>
.image-out-painting {
  text-align: center;
}
</style>
