import { createBrowserRouter } from 'react-router'
import { OrderDetailPage } from '../features/orders/pages/OrderDetailPage'
import { SettlementPage } from '../features/settlements/pages/SettlementPage'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { SignupPage } from '../features/auth/pages/SignupPage'
import { ProductListPage } from '../features/products/pages/ProductListPage'
import { ProductDetailPage } from '../features/products/pages/ProductDetailPage'
import { ProductEditorPage } from '../features/products/pages/ProductEditorPage'
import { ProductSaleStatusPage } from '../features/products/pages/ProductSaleStatusPage'
import { OrderCreatePage } from '../features/orders/pages/OrderCreatePage'
import { SellerProfilePage } from '../features/seller/pages/SellerProfilePage'
import { MyProfilePage } from '../features/auth/pages/MyProfilePage'
import { OrderListPage } from '../features/orders/pages/OrderListPage'
import { AppLayout } from '../shared/components/AppLayout'
import { AdminProductListPage } from '../features/admin/pages/AdminProductListPage'
import { AdminProductDetailPage } from '../features/admin/pages/AdminProductDetailPage'
import { WalletPage, WalletPreviewPage } from '../features/wallet/pages/WalletPage'
import { WalletTopupPage, WalletTopupPreviewPage } from '../features/wallet/pages/WalletTopupPage'
import { WalletTopupResultPage } from '../features/wallet/pages/WalletTopupResultPage'

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <ProductListPage /> },
      { path: 'products/:id', element: <ProductDetailPage /> },
      { path: 'mypage/products/new', element: <ProductEditorPage /> },
      { path: 'mypage/products/:id/edit', element: <ProductEditorPage /> },
      { path: 'mypage/products/:id/sale-status', element: <ProductSaleStatusPage /> },
      { path: 'products/:id/order', element: <OrderCreatePage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignupPage /> },
      { path: 'orders', element: <OrderListPage /> },
      { path: 'orders/:id', element: <OrderDetailPage /> },
      { path: 'mypage', element: <WalletPage /> },
      { path: 'mypage/profile', element: <MyProfilePage /> },
      { path: 'mypage/seller-profile', element: <SellerProfilePage /> },
      { path: 'mypage/settlements', element: <SettlementPage /> },
      { path: 'mypage/wallet', element: <WalletPage /> },
      { path: 'mypage/wallet/preview', element: <WalletPreviewPage /> },
      { path: 'mypage/wallet/topup', element: <WalletTopupPage /> },
      { path: 'mypage/wallet/topup/preview', element: <WalletTopupPreviewPage /> },
      { path: 'mypage/wallet/topup/:id/success', element: <WalletTopupResultPage outcome="success" /> },
      { path: 'mypage/wallet/topup/:id/fail', element: <WalletTopupResultPage outcome="fail" /> },
      { path: 'mypage/wallet/topup/:id/status', element: <WalletTopupResultPage outcome="status" /> },
      { path: 'admin/products', element: <AdminProductListPage /> },
      { path: 'admin/products/:id', element: <AdminProductDetailPage /> },
      { path: '*', element: <h1>페이지를 찾을 수 없습니다.</h1> },
    ],
  },
])
