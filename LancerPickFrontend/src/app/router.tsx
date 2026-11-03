import { createBrowserRouter } from 'react-router'
import { OrderDetailPage } from '../features/orders/pages/OrderDetailPage'
import { SettlementPage } from '../features/settlements/SettlementPage'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { ProductListPage } from '../features/products/pages/ProductListPage'
import { SellerProfilePage } from '../features/seller/pages/SellerProfilePage'
import { OrderListPage } from '../features/orders/pages/OrderListPage'
import { AppLayout } from '../shared/components/AppLayout'
import { WalletPage, WalletPreviewPage } from '../features/wallet/pages/WalletPage'
import { WalletTopupPage, WalletTopupPreviewPage } from '../features/wallet/pages/WalletTopupPage'
import { WalletTopupResultPage } from '../features/wallet/pages/WalletTopupResultPage'

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <ProductListPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <section style={{ padding: '48px 0' }}><h1>회원가입</h1><p>회원가입은 준비 중입니다. 현재 화면은 예시 서비스 탐색용입니다.</p></section> },
      { path: 'orders', element: <OrderListPage /> },
      { path: 'orders/:id', element: <OrderDetailPage /> },
      { path: 'mypage', element: <WalletPage /> },
      { path: 'mypage/seller-profile', element: <SellerProfilePage /> },
      { path: 'mypage/settlements', element: <SettlementPage /> },
      { path: 'mypage/wallet', element: <WalletPage /> },
      { path: 'mypage/wallet/preview', element: <WalletPreviewPage /> },
      { path: 'mypage/wallet/topup', element: <WalletTopupPage /> },
      { path: 'mypage/wallet/topup/preview', element: <WalletTopupPreviewPage /> },
      { path: 'mypage/wallet/topup/:id/success', element: <WalletTopupResultPage outcome="success" /> },
      { path: 'mypage/wallet/topup/:id/fail', element: <WalletTopupResultPage outcome="fail" /> },
      { path: 'mypage/wallet/topup/:id/status', element: <WalletTopupResultPage outcome="status" /> },
      { path: '*', element: <h1>페이지를 찾을 수 없습니다.</h1> },
    ],
  },
])
