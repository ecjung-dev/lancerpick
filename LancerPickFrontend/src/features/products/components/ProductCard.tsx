import type { ProductSummary } from '../types'
import { CategoryIcon } from './CategoryIcon'
import { formatWon } from '../../../shared/lib/format'
import styles from './ProductCard.module.css'

export function ProductCard({ product, onSelect }: { product: ProductSummary; onSelect: () => void }) {
  return (
    <button className={styles.card} onClick={onSelect} type="button" aria-label={`${product.title} 상세 보기`}>
      <div className={styles.art}>
        <span className={styles.category}>{product.category.name}</span>
        <span className={styles.artFrame}><CategoryIcon name={product.category.name} size={54} /></span>
        <span className={styles.artWord}>{product.category.name === '개발' ? 'MAKE IT WORK.' : 'MAKE IT YOURS.'}</span>
      </div>
      <div className={styles.body}>
        <span className={styles.seller}>{product.seller.displayName}</span>
        <h3>{product.title}</h3>
        <div className={styles.bottom}><span>{product.durationDays}일 작업</span><strong>{formatWon(product.priceAmount)}</strong></div>
      </div>
    </button>
  )
}
