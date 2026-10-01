<script setup lang="ts">
import { computed } from 'vue';
import type { ResultFormatPresentation } from '../types/activityResult';
import { formatBlocks } from '../utils/resultFormat';

const props = defineProps<{ summaryText: string; presentation: ResultFormatPresentation | null }>();
const blocks = computed(() => formatBlocks(props.summaryText, props.presentation));
</script>

<template>
  <div class="result-presentation" aria-label="成果展示内容">
    <p v-for="block in blocks" :key="`${block.start}-${block.end}`" class="result-presentation-paragraph">
      <span v-for="(segment, index) in block.segments" :key="`${block.start}-${index}`" :class="['result-presentation-segment', { 'result-presentation-bold': segment.style.includes('BOLD'), 'result-presentation-italic': segment.style.includes('ITALIC') }]">{{ segment.text }}</span>
    </p>
  </div>
</template>
