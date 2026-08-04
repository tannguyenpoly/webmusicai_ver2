// src/main/resources/static/js/modules/payment.js
export const paymentModule = {
    data() {
        return {
            packages: [],
            myOrders: [],
            isLoadingPackages: false,
            isLoadingOrders: false,
            paymentPollingTimer: null,
            activePaymentOrderCode: null,
            selectedPkg: null,
        };
    },
    methods: {
        loadPackages() {
            this.isLoadingPackages = true;
            axios.get('/api/packages')
                .then(res => { this.packages = res.data; this.isLoadingPackages = false; })
                .catch(() => { this.isLoadingPackages = false; });
        },
        loadMyOrders() {
            if (!this.currentUser) return;
            this.isLoadingOrders = true;
            axios.get('/api/orders/my-orders')
                .then(res => { this.myOrders = Array.isArray(res.data) ? res.data : []; this.isLoadingOrders = false; })
                .catch(() => { this.isLoadingOrders = false; });
        },
        buyPackage(pkg) {
            if (!this.currentUser) { window.location.href = '/login'; return; }
            this.selectedPkg = pkg;

            Swal.fire({
                title: 'Chọn phương thức thanh toán',
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: 'SePay (Chuyển khoản QR)',
                cancelButtonText: 'VNPAY (Trực tuyến)',
                confirmButtonColor: '#16a34a',
                cancelButtonColor: '#0d6efd'
            }).then(result => {
                if (result.isConfirmed) {
                    this.confirmPayment('SEPAY');
                } else if (result.dismiss === Swal.DismissReason.cancel) {
                    this.confirmPayment('VNPAY');
                }
            });
        },
        confirmPayment(method) {
            Swal.fire({ title: 'Đang tạo đơn hàng...', allowOutsideClick: false, didOpen: () => { Swal.showLoading(); } });

            axios.post('/api/orders/create', { package_id: this.selectedPkg.id, payment_method: method })
                .then(res => {
                    if (method === "SEPAY") {
                        const data = res.data;
                        this.activePaymentOrderCode = data.order_invoice_number;
                        this.startOrderStatusPolling(data.order_invoice_number);
                        Swal.fire({
                            title: 'Quét mã QR thanh toán',
                            html: `
                                <div class="text-center">
                                    <img src="${data.qrUrl}" style="max-width: 280px; border-radius: 10px; border: 1px solid #ddd;" class="mb-3">
                                    <p class="mb-1">Số tiền: <b class="text-success">${this.formatPrice(this.selectedPkg.price)}đ</b></p>
                                    <p class="mb-1">Nội dung: <code class="text-danger">${data.order_invoice_number}</code></p>
                                    <p class="small text-muted mt-2">Đơn có hiệu lực trong 15 phút. Không đóng cửa sổ này khi đang chuyển khoản.</p>
                                    <div class="small text-success mt-3"><span class="spinner-border spinner-border-sm me-1"></span> Đang chờ ngân hàng xác nhận...</div>
                                </div>`,
                            showConfirmButton: false,
                            showDenyButton: true,
                            denyButtonText: 'Hủy thanh toán',
                            denyButtonColor: '#dc3545',
                            allowOutsideClick: false,
                            allowEscapeKey: false
                        }).then(result => {
                            if (result.isDenied && this.activePaymentOrderCode === data.order_invoice_number) {
                                this.cancelPaymentOrder(data.order_invoice_number);
                            }
                        });
                    } else {
                        if (res.data.paymentUrl) { window.location.href = res.data.paymentUrl; }
                    }
                })
                .catch(err => {
                    Swal.fire('Lỗi', err.response?.data || 'Không thể tạo đơn hàng!', 'error');
                });
        },
        cancelPaymentOrder(orderCode) {
            if (this.paymentPollingTimer) {
                clearInterval(this.paymentPollingTimer);
                this.paymentPollingTimer = null;
            }
            axios.post(`/api/orders/${encodeURIComponent(orderCode)}/cancel`)
                .then(() => {
                    this.activePaymentOrderCode = null;
                    this.loadMyOrders();
                    Swal.fire({
                        icon: 'info',
                        title: 'Đã hủy thanh toán',
                        text: 'Đơn chưa thanh toán đã được đóng.',
                        confirmButtonColor: '#16a34a'
                    });
                })
                .catch(err => {
                    Swal.fire({
                        icon: 'error',
                        title: 'Không thể hủy đơn',
                        text: err.response?.data?.message || 'Vui lòng kiểm tra lại trạng thái đơn hàng.'
                    });
                });
        },
        startOrderStatusPolling(orderCode) {
            if (this.paymentPollingTimer) clearInterval(this.paymentPollingTimer);
            let attempts = 0;
            this.paymentPollingTimer = setInterval(() => {
                attempts++;
                axios.get(`/api/orders/${encodeURIComponent(orderCode)}/status`)
                    .then(res => {
                        if (res.data.status === 'SUCCESS') {
                            clearInterval(this.paymentPollingTimer);
                            this.paymentPollingTimer = null;
                            this.activePaymentOrderCode = null;
                            this.loadMyOrders();
                            this.loadUserTokenBalance(this.currentUser);
                            Swal.fire({
                                icon: 'success',
                                title: 'Thanh toán thành công',
                                text: 'Token đã được cộng vào tài khoản.',
                                confirmButtonColor: '#16a34a'
                            });
                        } else if (res.data.status === 'CANCELLED' || res.data.status === 'EXPIRED') {
                            clearInterval(this.paymentPollingTimer);
                            this.paymentPollingTimer = null;
                            this.activePaymentOrderCode = null;
                            Swal.fire({
                                icon: 'info',
                                title: res.data.status === 'EXPIRED' ? 'Đơn đã hết hạn' : 'Đơn đã được hủy',
                                text: 'Không có token nào được cộng vào tài khoản.',
                                confirmButtonColor: '#16a34a'
                            });
                        } else if (attempts >= 300) {
                            clearInterval(this.paymentPollingTimer);
                            this.paymentPollingTimer = null;
                        }
                    })
                    .catch(() => {
                        if (attempts >= 300) {
                            clearInterval(this.paymentPollingTimer);
                            this.paymentPollingTimer = null;
                        }
                    });
            }, 3000);
        },
    }
};
