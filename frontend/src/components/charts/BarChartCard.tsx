import { BarChart2, Table2 } from 'lucide-react'
import { useId, useState, type ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export interface ChartDatum {
  label: string
  value: number
  /** Optional second line in the tooltip and table, e.g. "12 rated · 2 ∞". */
  detail?: string
}

interface BarChartCardProps {
  title: string
  description?: ReactNode
  data: ChartDatum[]
  /** "column" for time and ordered scales, "bar" for categories with long names. */
  orientation?: 'column' | 'bar'
  valueName: string
  formatValue?: (value: number) => string
  formatLabel?: (label: string) => string
  domain?: [number, number]
  emptyMessage: string
  height?: number
  footnote?: ReactNode
}

const AXIS = { fill: 'var(--color-ink-3)', fontSize: 12 }

function ChartTooltip({
  active,
  payload,
  valueName,
  formatValue,
  formatLabel,
}: {
  active?: boolean
  payload?: { payload: ChartDatum }[]
  valueName: string
  formatValue: (value: number) => string
  formatLabel: (label: string) => string
}) {
  const datum = active ? payload?.[0]?.payload : undefined
  if (!datum) return null
  return (
    <div className="rounded-lg border border-line-strong bg-surface-2 px-3 py-2 text-sm shadow-lg shadow-black/40">
      <div className="flex items-center gap-2">
        <span className="h-0.5 w-3 rounded bg-series-1" aria-hidden />
        <span className="num font-semibold text-ink">{formatValue(datum.value)}</span>
        <span className="text-ink-3">{valueName}</span>
      </div>
      <p className="mt-0.5 text-ink-2">{formatLabel(datum.label)}</p>
      {datum.detail && <p className="mt-0.5 text-xs text-ink-3">{datum.detail}</p>}
    </div>
  )
}

/**
 * One-series bar chart with a table view. The table carries every value the chart
 * shows, so nothing depends on hovering or on telling colours apart.
 */
export function BarChartCard({
  title,
  description,
  data,
  orientation = 'column',
  valueName,
  formatValue = (v) => v.toLocaleString('en-GB'),
  formatLabel = (l) => l,
  domain,
  emptyMessage,
  height = 240,
  footnote,
}: BarChartCardProps) {
  const [asTable, setAsTable] = useState(false)
  const headingId = useId()
  const isBar = orientation === 'bar'
  const chartHeight = isBar ? Math.max(120, data.length * 34 + 30) : height
  const labelWidth = isBar ? Math.min(170, Math.max(70, ...data.map((d) => formatLabel(d.label).length * 7))) : 0

  return (
    <section aria-labelledby={headingId} className="card flex flex-col p-5">
      <header className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 id={headingId} className="text-sm font-semibold text-ink">
            {title}
          </h2>
          {description && <p className="mt-0.5 text-xs text-ink-3">{description}</p>}
        </div>
        {data.length > 0 && (
          <button
            type="button"
            onClick={() => setAsTable((t) => !t)}
            className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-xs text-ink-3 hover:bg-surface-2 hover:text-ink"
            aria-pressed={asTable}
          >
            {asTable ? <BarChart2 className="size-3.5" /> : <Table2 className="size-3.5" />}
            {asTable ? 'Chart' : 'Table'}
          </button>
        )}
      </header>

      {data.length === 0 ? (
        <p className="flex flex-1 items-center justify-center rounded-xl border border-dashed border-line-strong px-4 py-10 text-center text-sm text-ink-3">
          {emptyMessage}
        </p>
      ) : asTable ? (
        <div className="max-h-80 overflow-y-auto">
          <table className="w-full text-sm">
            <caption className="sr-only">{title}</caption>
            <thead>
              <tr className="border-b border-line text-left text-xs text-ink-3">
                <th scope="col" className="py-2 font-medium">
                  Group
                </th>
                <th scope="col" className="py-2 text-right font-medium">
                  {valueName}
                </th>
              </tr>
            </thead>
            <tbody>
              {data.map((d) => (
                <tr key={d.label} className="border-b border-line/60">
                  <td className="py-1.5 text-ink-2">
                    {formatLabel(d.label)}
                    {d.detail && <span className="ml-2 text-xs text-ink-3">{d.detail}</span>}
                  </td>
                  <td className="num py-1.5 text-right text-ink">{formatValue(d.value)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div style={{ height: chartHeight }} role="img" aria-label={`${title}. Switch to the table for exact values.`}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={data}
              layout={isBar ? 'vertical' : 'horizontal'}
              margin={{ top: 4, right: 12, bottom: 0, left: 0 }}
              barCategoryGap={isBar ? 6 : '20%'}
            >
              <CartesianGrid
                stroke="var(--color-grid)"
                strokeWidth={1}
                vertical={isBar}
                horizontal={!isBar}
              />
              {isBar ? (
                <>
                  <XAxis type="number" tick={AXIS} axisLine={false} tickLine={false} allowDecimals={false} domain={domain ?? [0, 'auto']} />
                  <YAxis
                    type="category"
                    dataKey="label"
                    tick={AXIS}
                    tickFormatter={formatLabel}
                    axisLine={false}
                    tickLine={false}
                    width={labelWidth}
                  />
                </>
              ) : (
                <>
                  <XAxis dataKey="label" tick={AXIS} tickFormatter={formatLabel} axisLine={false} tickLine={false} interval="preserveStartEnd" minTickGap={8} />
                  <YAxis tick={AXIS} axisLine={false} tickLine={false} allowDecimals={false} width={32} domain={domain ?? [0, 'auto']} />
                </>
              )}
              <Tooltip
                cursor={{ fill: 'rgb(255 255 255 / 0.04)' }}
                content={<ChartTooltip valueName={valueName} formatValue={formatValue} formatLabel={formatLabel} />}
              />
              <Bar
                dataKey="value"
                name={valueName}
                fill="var(--color-series-1)"
                maxBarSize={24}
                radius={isBar ? [0, 4, 4, 0] : [4, 4, 0, 0]}
                activeBar={{ fill: '#dc9a1c' }}
                isAnimationActive={false}
              />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
      {footnote && <p className="mt-3 text-xs text-ink-3">{footnote}</p>}
    </section>
  )
}
