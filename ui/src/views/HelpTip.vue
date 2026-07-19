<template>
  <!-- Info icon with a hover tooltip. The tooltip's activator is this span
       (a real element), which is why it reliably shows — unlike a v-tooltip
       nested inside a <v-icon> whose slot is reserved for the glyph. -->
  <span class="help-tip">
    <v-icon size="14" icon="mdi-information-outline" />
    <v-tooltip activator="parent" location="top" max-width="280">
      <span class="help-tip-text">{{ text }}</span>
    </v-tooltip>
  </span>
</template>

<script setup>
defineProps({
  text: { type: String, default: '' },
})
</script>

<style scoped>
.help-tip {
  display: inline-flex;
  align-items: center;
  color: rgba(var(--v-theme-on-surface), 0.45);
  cursor: help;
  vertical-align: middle;
}
</style>

<style>
.help-tip-text {
  white-space: pre-line;
}
</style>
