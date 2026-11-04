export function myProductsReturnPath(value: unknown) {
  return typeof value === 'string' && (value === '/mypage/products' || value.startsWith('/mypage/products?'))
    ? value : '/mypage/products'
}
