// 백엔드는 비용을 USD로만 계산·저장한다. 화면에서만 고정 환율로 원화를 함께 보여준다(백엔드 미변경).
export const USD_TO_KRW = 1460
export const USD_TO_KRW_LABEL = USD_TO_KRW.toLocaleString('ko-KR') // "1,460"

/** USD 표기($x.xx ~ $x.xxxx). 소액 비용도 유효 자리를 보여주려고 최대 4자리까지 쓴다. */
export function formatUsd(usd: number): string {
  return `$${usd.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`
}

const KRW_FORMAT = new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' })

/** USD 금액을 고정 환율로 원화 환산해 표기(₩12,345). 원화는 소수점 없이 반올림된다. */
export function formatKrw(usd: number): string {
  return KRW_FORMAT.format(usd * USD_TO_KRW)
}
