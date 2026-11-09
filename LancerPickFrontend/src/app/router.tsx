import { createBrowserRouter } from 'react-router'
import { OrderDetailPage } from '../features/orders/pages/OrderDetailPage'
import { SettlementPage } from '../features/settlements/pages/SettlementPage'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { SignupPage } from '../features/auth/pages/SignupPage'
import { SignupVerifyPage } from '../features/auth/pages/SignupVerifyPage'
import { PasswordResetPage } from '../features/auth/pages/PasswordResetPage'
import { PasswordResetRequestPage } from '../features/auth/pages/PasswordResetRequestPage'
import { ProductListPage } from '../features/products/pages/ProductListPage'
import { ProductDetailPage } from '../features/products/pages/ProductDetailPage'
import { ProductEditorPage } from '../features/products/pages/ProductEditorPage'
import { ProductSaleStatusPage } from '../features/products/pages/ProductSaleStatusPage'
import { MyProductsPage } from '../features/products/pages/MyProductsPage'
import { MyFavoritesPage } from '../features/products/pages/MyFavoritesPage'
import { OrderCreatePage } from '../features/orders/pages/OrderCreatePage'
import { SellerProfilePage } from '../features/seller/pages/SellerProfilePage'
import { PublicSellerPage } from '../features/seller/pages/PublicSellerPage'
import { MyProfilePage } from '../features/auth/pages/MyProfilePage'
import { OrderListPage } from '../features/orders/pages/OrderListPage'
import { NotificationsPage } from '../features/notifications/pages/NotificationsPage'
import { AppLayout } from '../shared/components/AppLayout'
import { AdminProductListPage } from '../features/admin/pages/AdminProductListPage'
import { AdminProductDetailPage } from '../features/admin/pages/AdminProductDetailPage'
import { AdminInquiryListPage } from '../features/admin/pages/AdminInquiryListPage'
import { AdminInquiryDetailPage } from '../features/admin/pages/AdminInquiryDetailPage'
import { AdminReviewTimerPage } from '../features/admin/pages/AdminReviewTimerPage'
import { AdminFinancePage } from '../features/admin/pages/AdminFinancePage'
import { AdminOrderFinancePage } from '../features/admin/pages/AdminOrderFinancePage'
import { WalletPage, WalletPreviewPage } from '../features/wallet/pages/WalletPage'
import { WalletTopupPage, WalletTopupPreviewPage } from '../features/wallet/pages/WalletTopupPage'
import { WalletTopupResultPage } from '../features/wallet/pages/WalletTopupResultPage'

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <ProductListPage /> },
      { path: 'products', element: <ProductListPage view="catalog" /> },
      { path: 'products/:id', element: <ProductDetailPage /> },
      { path: 'sellers/:id', element: <PublicSellerPage /> },
      { path: 'mypage/products/new', element: <ProductEditorPage /> },
      { path: 'mypage/products', element: <MyProductsPage /> },
      { path: 'mypage/favorites', element: <MyFavoritesPage /> },
      { path: 'mypage/products/:id/edit', element: <ProductEditorPage /> },
      { path: 'mypage/products/:id/sale-status', element: <ProductSaleStatusPage /> },
      { path: 'products/:id/order', element: <OrderCreatePage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignupPage /> },
      { path: 'signup/verify', element: <SignupVerifyPage /> },
      { path: 'password-reset/request', element: <PasswordResetRequestPage /> },
      { path: 'password-reset', element: <PasswordResetPage /> },
      { path: 'orders', element: <OrderListPage /> },
      { path: 'orders/:id', element: <OrderDetailPage /> },
      { path: 'notifications', element: <NotificationsPage /> },
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
      { path: 'admin/inquiries', element: <AdminInquiryListPage /> },
      { path: 'admin/inquiries/:id', element: <AdminInquiryDetailPage /> },
      { path: 'admin/review-timers', element: <AdminReviewTimerPage /> },
      { path: 'admin/finance', element: <AdminFinancePage /> },
      { path: 'admin/finance/orders/:id', element: <AdminOrderFinancePage /> },
      { path: '*', element: <h1>페이지를 찾을 수 없습니다.</h1> },
    ],
  },
])
