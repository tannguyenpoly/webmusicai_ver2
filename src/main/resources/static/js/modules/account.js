window.MusicAIModules = window.MusicAIModules || {};
window.MusicAIModules.account = {
    methods: {
        handleLogin() {
            this.loginError = '';
            if (this.loginForm && !this.loginForm.username.trim()) {
                this.loginError = 'Vui lòng nhập tên đăng nhập.';
                Swal.fire({ icon: 'warning', title: 'Thiếu thông tin', text: this.loginError });
                return;
            }
            if (this.loginForm && !this.loginForm.password.trim()) {
                this.loginError = 'Vui lòng nhập mật khẩu.';
                Swal.fire({ icon: 'warning', title: 'Thiếu thông tin', text: this.loginError });
                return;
            }
            const btn = document.getElementById('submit-btn');
            if (btn) {
                btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang kết nối...';
                btn.disabled = true;
            }
            axios.post('/api/auth/login', this.loginForm)
                .then(response => {
                    localStorage.setItem('music_username', response.data.username);
                    localStorage.setItem('music_is_admin', response.data.isAdmin);
                    if (btn) {
                        btn.innerHTML = '<i class="ti ti-check"></i> Kích hoạt thành công!';
                        btn.style.background = '#15803d';
                    }
                    this.Toast.fire({ icon: 'success', title: `Đăng nhập thành công! Chào mừng ${response.data.username}.` });
                    setTimeout(() => {
                        if (response.data.isAdmin) window.location.href = '/admin';
                        else window.location.href = '/';
                    }, 1000);
                })
                .catch(err => {
                    if (btn) {
                        btn.innerHTML = '<i class="ti ti-bolt"></i> Kích hoạt hệ thống';
                        btn.disabled = false;
                    }
                    let msg = 'Không thể đăng nhập. Vui lòng thử lại.';
                    if (err.response && err.response.status === 403) msg = err.response.data || 'Tài khoản đã bị khóa!';
                    else if (err.response && err.response.data) msg = err.response.data.message || err.response.data || msg;
                    this.loginError = typeof msg === 'string' ? msg : 'Không thể đăng nhập. Vui lòng thử lại.';
                    Swal.fire({ icon: 'error', title: 'Đăng nhập thất bại', text: this.loginError, confirmButtonColor: '#16a34a' });
                });
        },

        handleRegister() {
            if (!this.registerForm.username.trim() || this.registerForm.username.trim().includes(' ')) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Tên đăng nhập không hợp lệ!' });
                return;
            }
            if (!this.registerForm.fullname.trim() || !this.registerForm.email.trim() || !this.registerForm.password.trim()) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng điền đủ thông tin!' });
                return;
            }
            if (this.registerForm.password !== this.registerForm.confirmPassword) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Mật khẩu không trùng khớp!' });
                return;
            }
            const btn = document.querySelector('button[type="submit"]');
            if (btn) { btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang khởi tạo...'; btn.disabled = true; }
            axios.post('/api/auth/register', this.registerForm)
                .then(() => {
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tạo tài khoản thành công!', confirmButtonColor: '#16a34a' })
                        .then(() => { window.location.href = '/login'; });
                })
                .catch(error => {
                    if (btn) { btn.innerHTML = '<i class="ti ti-user-plus"></i> Khởi tạo tài khoản'; btn.disabled = false; }
                    const errorMsg = error.response && error.response.data ? (error.response.data.message || error.response.data) : 'Đăng ký thất bại.';
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: errorMsg });
                });
        },

        openForgotPasswordModal() {
            this.forgotPasswordForm = { email: '', otp: '', newPassword: '', confirmPassword: '', step: 1, isSending: false };
            const modalElem = document.getElementById('forgotPasswordModal');
            if (modalElem) { const modal = new bootstrap.Modal(modalElem); modal.show(); }
        },

        sendForgotPasswordOtp() {
            if (!this.forgotPasswordForm.email || !this.forgotPasswordForm.email.trim()) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng nhập Email!' });
                return;
            }
            this.forgotPasswordForm.isSending = true;
            axios.post('/api/auth/forgot-password', { email: this.forgotPasswordForm.email.trim() })
                .then(res => {
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Đã gửi OTP qua Email.' });
                    this.forgotPasswordForm.step = 2;
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: err.response?.data?.message || 'Lỗi gửi OTP.' });
                })
                .finally(() => { this.forgotPasswordForm.isSending = false; });
        },

        submitResetPassword() {
            if (!this.forgotPasswordForm.otp || !this.forgotPasswordForm.newPassword) {
                Swal.fire({ icon: 'warning', title: 'Thiếu thông tin', text: 'Vui lòng nhập OTP và mật khẩu mới.' });
                return;
            }
            if (this.forgotPasswordForm.newPassword !== this.forgotPasswordForm.confirmPassword) {
                Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Mật khẩu xác nhận không khớp.' });
                return;
            }
            this.forgotPasswordForm.isSending = true;
            axios.post('/api/auth/reset-password', {
                email: this.forgotPasswordForm.email.trim(),
                otp: this.forgotPasswordForm.otp.trim(),
                newPassword: this.forgotPasswordForm.newPassword
            })
                .then(res => {
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Đặt lại mật khẩu thành công!' })
                        .then(() => {
                            if (this.showProfileModal) {
                                this.showProfileModal = false;
                                this.handleLogout(false);
                                return;
                            }
                            const modalElem = document.getElementById('forgotPasswordModal');
                            if (modalElem) { const modal = bootstrap.Modal.getInstance(modalElem); modal.hide(); }
                        });
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: err.response?.data?.message || 'Đặt lại mật khẩu thất bại!' });
                })
                .finally(() => { this.forgotPasswordForm.isSending = false; });
        },

        handleLogout(showConfirm = true) {
            const executeLogout = () => {
                if (this.stompClient) { try { this.stompClient.disconnect(); } catch(e) {} }
                if (this.presenceHeartbeatTimer) clearInterval(this.presenceHeartbeatTimer);
                axios.post('/api/auth/logout').finally(() => {
                    localStorage.removeItem('music_username');
                    localStorage.removeItem('jwt_token');
                    localStorage.removeItem('music_is_admin');
                    window.location.href = '/';
                });
            };
            if (!showConfirm) executeLogout();
            else Swal.fire({ title: 'Xác nhận đăng xuất?', icon: 'question', showCancelButton: true, confirmButtonColor: '#16a34a', confirmButtonText: 'Đăng xuất' })
                .then(result => { if (result.isConfirmed) executeLogout(); });
        },

        openProfileModal() {
            this.profileModalTab = 'info';
            this.profileModalError = '';
            this.changePasswordForm = { oldPassword: '', newPassword: '', confirmNewPassword: '' };
            this.passwordResetMode = false;
            if (!this.currentUser) return;
            axios.get(`/api/users/${this.currentUser}/profile`)
                .then(response => {
                    const data = response.data;
                    this.profileForm.fullname = data.fullname || '';
                    this.profileForm.email = data.email || '';
                    this.profileForm.photo = data.photo || '';
                    this.profileForm.authProvider = data.authProvider || 'LOCAL';
                    this.authProvider = data.authProvider || 'LOCAL';
                    this.hasLocalPassword = data.hasLocalPassword !== false;
                    this.userTier = data.accountTier || 'FREE';
                    this.userTierExpiresAt = data.tierExpiresAt || null;
                    this.showProfileModal = true;
                })
                .catch(error => { Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Không thể tải thông tin cá nhân' }); });
        },

        closeProfileModal() {
            this.showProfileModal = false;
        },

        submitUpdateProfile() {
            axios.put(`/api/users/${this.currentUser}/profile`, this.profileForm)
                .then(response => {
                    this.Toast.fire({ icon: 'success', title: 'Cập nhật hồ sơ thành công!' });
                    if (window.location.pathname === '/profile') this.loadProfilePageData();
                    this.showProfileModal = false;
                })
                .catch(error => {
                    Swal.fire({ icon: 'error', title: 'Cập nhật thất bại', text: 'Vui lòng kiểm tra lại thông tin.' });
                });
        },

        submitChangePassword() {
            if (this.changePasswordForm.newPassword !== this.changePasswordForm.confirmNewPassword) {
                Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Mật khẩu không khớp!' });
                return;
            }
            axios.put(`/api/users/${this.currentUser}/change-password`, this.changePasswordForm)
                .then(response => {
                    this.showProfileModal = false;
                    Swal.fire({ icon: 'success', title: 'Thành công!', text: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại.', confirmButtonColor: '#16a34a' })
                        .then(() => { this.handleLogout(false); });
                })
                .catch(error => {
                    Swal.fire({ icon: 'error', title: 'Đổi mật khẩu thất bại', text: error.response?.data?.message || 'Có lỗi xảy ra.' });
                });
        },

        loadPackages() {
            this.isLoadingPackages = true;
            axios.get('/api/packages')
                .then(res => { this.packages = res.data; this.isLoadingPackages = false; })
                .catch(() => { this.isLoadingPackages = false; });
        },

        packageBenefits(tierCode) {
            const benefits = {
                CREATOR: [
                    '60 Credit sử dụng trong 30 ngày.',
                    'Tạo nhạc bằng AudioCraft và MusicAPI.ai.',
                    'Tạo nhạc không lời hoặc để AI gợi ý lời.',
                    'Chọn thời lượng tối đa 30 giây.',
                    'Có thể chọn giọng nam hoặc nữ khi dùng MusicAPI.ai.'
                ],
                PRO: [
                    '180 Credit sử dụng trong 30 ngày.',
                    'Dùng AudioCraft, MusicAPI.ai, ACE-Step và Suno.',
                    'Tự nhập lời nhạc hoặc dùng AI gợi ý lời.',
                    'Chọn thời lượng tối đa 60 giây.',
                    'Phân tích file nhạc tham khảo để gợi ý thể loại.'
                ],
                STUDIO: [
                    '500 Credit sử dụng trong 30 ngày.',
                    'Dùng tất cả mô hình AI hiện có.',
                    'Tự nhập lời nhạc hoặc dùng AI gợi ý lời.',
                    'Chọn thời lượng tối đa 2 phút.',
                    'Phân tích file nhạc tham khảo để gợi ý thể loại.'
                ]
            };
            return benefits[tierCode] || [];
        },

        loadMyOrders() {
            if (!this.currentUser) return;
            this.isLoadingOrders = true;
            axios.get('/api/orders/my-orders')
                .then(res => { this.myOrders = Array.isArray(res.data) ? res.data : []; this.isLoadingOrders = false; })
                .catch(() => { this.isLoadingOrders = false; });
        },

        resetPaymentHistoryFilters() {
            this.paymentHistoryFilters = { status: 'ALL', year: 'ALL', month: 'ALL' };
            this.paymentHistoryPage = 1;
        },

        resetPaymentHistoryPage() {
            this.paymentHistoryPage = 1;
        },

        changePaymentHistoryPage(direction) {
            const nextPage = this.paymentHistoryPage + direction;
            if (nextPage >= 1 && nextPage <= this.paymentHistoryTotalPages) {
                this.paymentHistoryPage = nextPage;
            }
        },

        orderStatusLabel(status) {
            const labels = {
                PENDING: 'Chờ thanh toán',
                SUCCESS: 'Thành công',
                REVIEW: 'Cần đối soát',
                FAILED: 'Thất bại',
                CANCELLED: 'Đã hủy',
                EXPIRED: 'Hết hạn'
            };
            return labels[status] || status || 'Không xác định';
        },

        orderStatusClass(status) {
            return `payment-status is-${String(status || 'unknown').toLowerCase()}`;
        },

        // --- HÀM THANH TOÁN TÍCH HỢP SEPAY QR ---
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
                                    <p class="small text-muted mt-2 mb-1">Thời gian chờ thanh toán: <b id="payment-countdown" class="text-danger">15:00</b></p>
                                    <p class="small text-muted mb-0">Khi thời gian kết thúc, không dùng lại mã QR này để chuyển khoản.</p>
                                    <div class="small text-success mt-3"><span class="spinner-border spinner-border-sm me-1"></span> Đang chờ ngân hàng xác nhận...</div>
                                </div>`,
                            showConfirmButton: false,
                            showDenyButton: true,
                            denyButtonText: 'Dừng chờ thanh toán',
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

    }
};
