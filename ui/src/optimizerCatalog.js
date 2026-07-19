// Optimizer-profile presentation (ProvOptimizer). An optimizer picks whether
// the lookup prioritises cost or CO₂ (`mode`), and optionally restricts to the
// most efficient / latest (P1) type only (`p1TypeOnly`).

/** The two optimization modes (Optimizer enum), with label i18n keys + icons. */
export const OPTIMIZER_MODES = [
  { value: 'COST', labelKey: 'prov.quote.optimizer.mode.cost', icon: 'mdi-currency-usd' },
  { value: 'CO2', labelKey: 'prov.quote.optimizer.mode.co2', icon: 'mdi-leaf' },
]

/** Icon for an optimizer's mode. */
export function optimizerModeIcon(mode) {
  return (OPTIMIZER_MODES.find((m) => m.value === mode) || OPTIMIZER_MODES[0]).icon
}

/** Short summary shown beside an optimizer name (mode + P1 marker). */
export function optimizerSummary(optimizer, t) {
  if (!optimizer) return ''
  const m = OPTIMIZER_MODES.find((x) => x.value === optimizer.mode)
  const label = m && t ? t(m.labelKey) : optimizer.mode || ''
  return optimizer.p1TypeOnly ? `${label} · P1` : label
}

/** Build the REST edition payload (OptimizerEditionVo) from an editor form. */
export function optimizerPayload(form) {
  return {
    id: form.id ?? undefined,
    name: form.name,
    mode: form.mode || 'COST',
    p1TypeOnly: !!form.p1TypeOnly,
  }
}
