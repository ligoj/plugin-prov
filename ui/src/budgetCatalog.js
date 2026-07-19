// Budget-profile presentation (ProvBudget). A budget caps the accepted
// upfront/initial cost via `initialCost`; `requiredInitialCost` is the
// read-only amount actually required by the current quote.
import { formatCost } from './quoteFormatters.js'

/** Short summary shown beside a budget name — its available cash, or ''. */
export function budgetSummary(budget, currency) {
  if (!budget || !(Number(budget.initialCost) > 0)) return ''
  return formatCost(Number(budget.initialCost), currency)
}

/** Build the REST edition payload (BudgetEditionVo) from an editor form. */
export function budgetPayload(form) {
  return {
    id: form.id ?? undefined,
    name: form.name,
    initialCost: Number(form.initialCost) || 0,
  }
}
