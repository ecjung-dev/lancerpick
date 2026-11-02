import { createBrowserRouter } from 'react-router'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { ProductListPage } from '../features/products/pages/ProductListPage'
import { OrderListPage } from '../features/orders/pages/OrderListPage'
import { AppLayout } from '../shared/components/AppLayout'
import { WalletPage, WalletPreviewPage } from '../features/wallet/pages/WalletPage'

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <ProductListPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <section style={{ padding: '48px 0' }}><h1>회원가입</h1><p>회원가입은 준비 중입니다. 현재 화면은 예시 서비스 탐색용입니다.</p></section> },
      { path: 'orders', element: <OrderListPage /> },
      { path: 'mypage', element: <WalletPage /> },
      { path: 'mypage/wallet', element: <WalletPage /> },
      { path: 'mypage/wallet/preview', element: <WalletPreviewPage /> },
      { path: '*', element: <h1>페이지를 찾을 수 없습니다.</h1> },
    ],
  },
])
