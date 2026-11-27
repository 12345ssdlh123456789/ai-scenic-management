<template>
  <div class="ai-chat-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 智能客服</h1>
        <p class="page-subtitle">基于 RAG 知识库的景区智能问答助手</p>
      </div>
      <el-tag type="success" size="large" effect="dark" class="knowledge-badge">
        <CircleCheck /> 知识库已加载 {{ knowledgeCount }} 条
      </el-tag>
    </div>

    <!-- 快捷问题建议 -->
    <el-card shadow="never" class="quick-questions-card" v-if="messages.length === 0">
      <div class="quick-header">
        <h3 class="quick-title">常见问题</h3>
        <p class="quick-desc">点击下方问题快速开始咨询</p>
      </div>
      <div class="quick-grid">
        <el-button
          v-for="(q, index) in quickQuestions"
          :key="index"
          variant="text"
          class="quick-btn"
          @click="quickAsk(q)"
        >
          {{ q }}
        </el-button>
      </div>
    </el-card>

    <!-- 聊天主卡片 -->
    <el-card shadow="never" class="chat-card">
      <!-- 聊天记录区域 -->
      <div ref="chatContainer" class="chat-container">
        <div v-if="messages.length === 0" class="welcome-msg">
          <div class="welcome-avatar">
            <Monitor />
          </div>
          <h3 class="welcome-title">您好！我是景区智能客服</h3>
          <p class="welcome-desc">您可以询问门票价格、开放时间、景点介绍、交通方式等问题</p>
        </div>

        <div v-else class="messages-list">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-row"
            :class="msg.role"
          >
            <div class="message-bubble">
              <div class="avatar" :class="msg.role">
                <component :is="msg.role === 'user' ? 'UserFilled' : 'Monitor'" />
              </div>
              <div class="bubble-content">
                <div class="msg-text" v-html="formatText(msg.content)"></div>
                <div class="msg-time">{{ msg.time }}</div>
              </div>
            </div>
          </div>

          <!-- 正在思考状态 -->
          <div v-if="loading" class="message-row assistant thinking">
            <div class="message-bubble">
              <div class="avatar assistant">
                <Monitor />
              </div>
              <div class="bubble-content thinking-bubble">
                <div class="thinking-dots">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div class="msg-time">正在思考...</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 5 }"
          placeholder="请输入您的问题..."
          @keydown.enter.exact="sendMessage"
          :disabled="loading"
          clearable
          class="chat-input"
        >
          <template #append>
            <el-button
              @click="sendMessage"
              :loading="loading"
              type="primary"
              class="send-btn"
              :disabled="!inputText.trim() || loading"
            >
              <ArrowRight />
            </el-button>
          </template>
        </el-input>
        <div class="input-hint">
          <kbd>Enter</kbd> 发送 · <kbd>Shift</kbd> + <kbd>Enter</kbd> 换行
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { chatAI, getStats } from '@/api/ai'
import {
  CircleCheck, ArrowRight, Monitor
} from '@element-plus/icons-vue'

const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const knowledgeCount = ref(0)
const chatContainer = ref(null)

const quickQuestions = [
  '门票多少钱？',
  '园区几点开门？',
  '有哪些好玩的项目？',
  '怎么到达景区？',
  'VIP 有什么优惠？',
  '可以带宠物吗？'
]

onMounted(async () => {
  try {
    const res = await getStats()
    knowledgeCount.value = res.data.count
  } catch (e) {
    console.error('获取知识库统计失败', e)
  }
})

const sendMessage = async () => {
  const question = inputText.value.trim()
  if (!question || loading.value) return

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: question,
    time: getCurrentTime()
  })
  inputText.value = ''

  loading.value = true
  await scrollToBottom()

  try {
    const res = await chatAI({ question })
    messages.value.push({
      role: 'assistant',
      content: res.data.answer,
      time: getCurrentTime()
    })
  } catch (e) {
    const errMsg = e?.message || '抱歉，服务暂时不可用，请稍后再试。'
    let displayMsg = errMsg
    if (errMsg.includes('AI 客服暂未配置') || errMsg.includes('未配置') || errMsg.includes('API Key')) {
      displayMsg = '⚠️ AI 客服暂未配置或 API Key 无效，请联系管理员检查 NVIDIA_API_KEY 环境变量。'
    } else if (errMsg.includes('AI 服务') || errMsg.includes('不可用') || errMsg.includes('认证失败')) {
      displayMsg = '⚠️ 抱歉，AI 服务调用失败，请稍后再试或联系人工客服。'
    }
    messages.value.push({
      role: 'assistant',
      content: displayMsg,
      time: getCurrentTime()
    })
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

const quickAsk = (question) => {
  inputText.value = question
  sendMessage()
}

const getCurrentTime = () => {
  const now = new Date()
  return now.getHours().toString().padStart(2, '0') + ':' + now.getMinutes().toString().padStart(2, '0')
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

const formatText = (text) => {
  // 先做真正的 HTML 实体转义，再交给 v-html 渲染。
  // 原实现是 .replace(/&/g,'&') 这类"自己换自己"的恒等替换，等于没有转义，
  // 任何 <script>/<img onerror> 都会被执行（XSS）。
  const escaped = String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')

  // 模型返回的是 Markdown（**加粗**、- 列表、### 标题…），
  // 不处理就会把 ** 原样显示出来，看起来很"脏"。
  //
  // 安全前提：上面已经完成实体转义，因此这里只做「把纯文本标记
  // 替换成标签」的白名单转换，不会引入可执行内容——
  // 用户/模型输入里的 < > 此时已是 &lt; &gt;，无法构造出新标签。
  return escaped
    // 加粗：**text** / __text__
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/__(.+?)__/g, '<strong>$1</strong>')
    // 行内代码：`code`
    .replace(/`([^`]+?)`/g, '<code class="msg-code">$1</code>')
    // 标题：### / ## / #（行首）
    .replace(/^#{1,6}\s*(.+)$/gm, '<strong class="msg-heading">$1</strong>')
    // 无序列表：- / * / •（行首）
    .replace(/^\s*[-*•]\s+(.+)$/gm, '<span class="msg-li">$1</span>')
    // 有序列表：1. 2.（行首）
    .replace(/^\s*(\d+)\.\s+(.+)$/gm, '<span class="msg-li"><b>$1.</b> $2</span>')
    // 换行：先处理段落（连续两个换行），再处理单个换行
    .replace(/\n{2,}/g, '<br><br>')
    .replace(/\n/g, '<br>')
}
</script>

<style scoped>
/* ===== AI 智能客服页面样式 ===== */

.ai-chat-page {
  /* 对话内容过宽会影响阅读（一行太长眼睛难以换行），
     但 900px 在 1600px 视口下两侧留白过多、显得局促。
     1280px 与全站 --content-max-width 的观感更一致，
     同时正文仍由气泡的最大宽度约束，不会真的拉成超长行。 */
  max-width: 1280px;
  margin: 0 auto;
  /* 内容区不再统一加内边距（避免与各页自带 padding 叠加），
     这里补齐本页自己的留白 */
  padding: var(--space-6) var(--space-6) var(--space-10);
}

.page-header {
  display: flex;
  /* 徽章与标题块顶部对齐，而不是沉到底部——
     align-items: flex-end 会让徽章比标题低一整行，看起来错位。 */
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
  gap: var(--space-4);
}

.knowledge-badge {
  font-weight: 500 !important;
  display: inline-flex !important;
  align-items: center !important;
  gap: var(--space-2) !important;
  /* 与标题首行视觉居中对齐 */
  margin-top: 2px;
  flex-shrink: 0;
}

/* 快捷问题卡片 */
.quick-questions-card {
  margin-bottom: var(--space-6);
  border: 1px solid var(--color-border-light);
  background: var(--color-bg);
}

.quick-header {
  margin-bottom: var(--space-4);
}

.quick-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-1);
}

.quick-desc {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0;
}

.quick-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.quick-btn {
  height: 36px !important;
  padding: 0 16px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  border-radius: var(--radius-full) !important;
  border: 1px solid var(--color-border) !important;
  color: var(--color-text-secondary) !important;
  background: var(--color-bg-card) !important;
  transition: all var(--transition-fast) !important;
}

.quick-btn:hover {
  border-color: var(--color-primary) !important;
  color: var(--color-primary) !important;
  background: var(--color-primary-bg) !important;
  transform: translateY(-1px);
}

/* 聊天卡片
   ⚠️ 高度不能写死。首页有"常见问题"卡片（约 190px），一旦开始对话该卡片
   消失——若用固定 100vh-280px，两种情况总高度不一致，会把输入框顶到
   屏幕外（需要滚动才能打字）。

   改用 100dvh（动态视口高度，比 100vh 更贴近真实可视区）减去
   顶栏 + 页面上下留白 + 页头，并保留一个可按需覆盖的变量。 */
.chat-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
  display: flex;
  flex-direction: column;
  height: calc(100dvh - var(--header-height) - 260px);
  min-height: 420px;
  overflow: hidden;
}

.chat-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

/* 聊天容器 */
.chat-container {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6);
  display: flex;
  flex-direction: column;
}

/* 欢迎态 */
.welcome-msg {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--color-text-tertiary);
}

.welcome-avatar {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--color-primary-bg) 0%, var(--color-info-bg) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-5);
  box-shadow: var(--shadow-md);
}

.welcome-avatar :deep(svg) {
  width: 36px;
  height: 36px;
  color: var(--color-primary);
}

.welcome-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2);
}

.welcome-desc {
  font-size: 14px;
  color: var(--color-text-tertiary);
  margin: 0;
  max-width: 400px;
  line-height: 1.6;
}

/* 消息列表 */
.messages-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding-bottom: var(--space-4);
}

.message-row {
  display: flex;
  animation: messageSlideIn 0.3s ease;
}

@keyframes messageSlideIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.message-row.thinking {
  animation: thinkingPulse 1.5s ease-in-out infinite;
}

@keyframes thinkingPulse {
  0%, 100% { opacity: 0.7; }
  50% { opacity: 1; }
}

.message-bubble {
  display: flex;
  gap: var(--space-3);
  max-width: 75%;
}

.message-row.user .message-bubble {
  flex-direction: row-reverse;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}

.avatar.user {
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  color: white;
}

.avatar.assistant {
  background: linear-gradient(135deg, var(--color-success) 0%, #059669 100%);
  color: white;
}

.avatar :deep(svg) {
  width: 20px;
  height: 20px;
}

.bubble-content {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-xl);
  min-width: 60px;
}

.message-row.user .bubble-content {
  background: var(--color-primary);
  color: white;
  border-bottom-right-radius: var(--radius-sm);
}

.message-row.assistant .bubble-content {
  background: var(--color-bg);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
  border-bottom-left-radius: var(--radius-sm);
  box-shadow: var(--shadow-sm);
}

.thinking-bubble {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.thinking-dots {
  display: flex;
  gap: 4px;
}

.thinking-dots span {
  width: 8px;
  height: 8px;
  border-radius: var(--radius-full);
  background: var(--color-primary);
  animation: dotBounce 1.4s ease-in-out infinite both;
}

.thinking-dots span:nth-child(1) { animation-delay: -0.32s; }
.thinking-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes dotBounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.msg-text {
  line-height: 1.7;
  /* 换行已由 formatText 转成 <br>，这里不能再保留 pre-wrap：
     两者叠加会让每个换行渲染成双倍行距。 */
  white-space: normal;
  word-break: break-word;
}

/* Markdown 转换后的元素样式（见 formatText） */
.msg-text :deep(.msg-heading) {
  display: inline-block;
  font-weight: 650;
  color: var(--color-text-primary);
  margin-top: var(--space-1);
}

.msg-text :deep(.msg-li) {
  display: block;
  position: relative;
  padding-left: var(--space-4);
  margin: 2px 0;
}

/* 列表项前的圆点 */
.msg-text :deep(.msg-li)::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 0.72em;
  width: 4px;
  height: 4px;
  border-radius: var(--radius-full);
  background: currentColor;
  opacity: 0.5;
}

.msg-text :deep(.msg-code) {
  font-family: var(--font-mono);
  font-size: 0.9em;
  padding: 1px 5px;
  border-radius: var(--radius-sm);
  background: var(--slate-100);
  color: var(--brand-700);
}

.msg-text :deep(strong) {
  font-weight: 650;
}

.msg-time {
  font-size: 11px;
  opacity: 0.6;
  margin-top: var(--space-2);
  text-align: right;
}

.message-row.assistant .msg-time {
  text-align: left;
}

/* 输入区域 */
.input-area {
  padding: var(--space-5);
  border-top: 1px solid var(--color-divider);
  background: var(--color-bg);
}

.chat-input :deep(.el-input__inner) {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--color-border) !important;
  background: var(--color-bg-card) !important;
  padding-right: 56px !important;
  font-size: 14px !important;
  line-height: 1.6 !important;
  transition: all var(--transition-fast) !important;
}

.chat-input :deep(.el-input__inner:focus) {
  border-color: var(--color-primary) !important;
  box-shadow: 0 0 0 3px var(--color-primary-bg) !important;
}

.chat-input :deep(.el-input__suffix) {
  right: 0 !important;
}

.send-btn {
  height: 40px !important;
  width: 40px !important;
  border-radius: var(--radius-full) !important;
  background: var(--color-primary) !important;
  border: none !important;
  box-shadow: var(--shadow-sm) !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  transition: all var(--transition-fast) !important;
}

.send-btn:hover:not(:disabled) {
  background: var(--color-primary-dark) !important;
  transform: scale(1.05) !important;
}

.send-btn:disabled {
  opacity: 0.5 !important;
  cursor: not-allowed !important;
}

.send-btn :deep(svg) {
  width: 18px;
  height: 18px;
}

.input-hint {
  display: flex;
  justify-content: center;
  gap: var(--space-4);
  margin-top: var(--space-3);
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.input-hint kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 20px;
  padding: 0 6px;
  font-size: 10px;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-weight: 600;
  color: var(--color-text-secondary);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  box-shadow: 0 1px 0 var(--color-border);
}

/* 响应式 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .knowledge-badge {
    width: 100%;
    justify-content: center;
  }

  .message-bubble {
    max-width: 85%;
  }

  .chat-card {
    height: calc(100vh - 240px);
    min-height: 500px;
  }

  .quick-grid {
    justify-content: center;
  }
}

/* 减少动画偏好 */
@media (prefers-reduced-motion: reduce) {
  .message-row,
  .thinking-dots span,
  .message-row.thinking {
    animation: none !important;
  }
  .thinking-dots span { transform: scale(1); }
}
</style>