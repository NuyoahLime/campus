<script setup lang="ts">
import type { RankingEntry } from '../types/ranking';

defineProps<{ entries: RankingEntry[] }>();
</script>

<template>
  <div class="ranking-entry-table-wrap">
    <div v-if="entries.length === 0" class="project-state">
      <strong>暂无排行条目</strong>
      <p>此快照当前没有可展示的条目。</p>
    </div>
    <table v-else class="student-score-table ranking-table">
      <thead>
        <tr><th>名次</th><th>学生</th><th>学校</th><th>成绩</th></tr>
      </thead>
      <tbody>
        <tr v-for="entry in entries" :key="`${entry.rankPosition}-${entry.studentDisplayName}`">
          <td><strong>第 {{ entry.rankPosition }} 名</strong></td>
          <td>{{ entry.studentDisplayName }}</td>
          <td>{{ entry.schoolName || '未记录' }}</td>
          <td><strong>{{ entry.scoreDisplayValue }}</strong></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
