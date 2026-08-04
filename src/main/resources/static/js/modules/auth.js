// src/main/resources/static/js/modules/auth.js
export const authModule = {
    data() {
        return {
            loginForm: { username: '', password: '' },
            registerForm: { username: '', fullname: '', email: '', password: '', confirmPassword: '' },
            forgotPasswordForm: { email: '', otp: '', newPassword: '', confirmPassword: '', step: 1, isSending: false },
        };
    },
    methods: {
        handleLogin() {
            if (this.loginForm && !this.loginForm.username.trim()) {
                this.Toast.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập tên đăng nhập!' });
                return;
            }
            if (this.loginForm && !this.loginForm.password.trim()) {
                this.Toast.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập mật khẩu!' });
                return;
            }
            const btn = document.getElementById('submit-btn');
            if (btn) {
                btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang kết nối...';
                btn.disabled = true;
            }
            axios.post('/api/auth/login', this.loginForm)
                .then(response => {
                    const guestId = localStorage.getItem('music_guest_id');
                    if (guestId) {
                        this.migrateGuestSongs(guestId, response.data.username);
                    }
                    localStorage.setItem('music_username', response.data.username);
                    localStorage.setItem('music_is_admin', response.data.isAdmin);
                    if (btn) {
                        btn.innerHTML = '<i class="ti ti-check"></i> Kích hoạt thành công!';
                        btn.style.background = '#15803d';
                    }
                    this.Toast.fire({ icon: 'success', title: `Khởi động hệ thống thành công! Chào mừng ${response.data.username}.` });
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
                    let msg = 'Tài khoản hoặc mật khẩu không chính xác.';
                    if (err.response && err.response.status === 403) msg = err.response.data || 'Tài khoản đã bị khóa!';
                    else if (err.response && err.response.data) msg = err.response.data.message || err.response.data || msg;
                    Swal.fire({ icon: 'error', title: 'Đăng nhập thất bại', text: msg, confirmButtonColor: '#16a34a' });
                });
        },
        handleRegister() {
            if (!this.registerForm.username.trim() || this.registerForm.username.trim().includes(' ')) {
                this.Toast.fire({ icon: 'warning', title: 'Lỗi', text: 'Tên đăng nhập không hợp lệ!' });
                return;
            }
            if (!this.registerForm.fullname.trim() || !this.registerForm.email.trim() || !this.registerForm.password.trim()) {
                this.Toast.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng điền đủ thông tin!' });
                return;
            }
            if (this.registerForm.password !== this.registerForm.confirmPassword) {
                this.Toast.fire({ icon: 'warning', title: 'Lỗi', text: 'Mật khẩu không trùng khớp!' });
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
                this.Toast.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng nhập Email!' });
                return;
            }
            this.forgotPasswordForm.isSending = true;
            axios.post('/api/auth/forgot-password', { email: this.forgotPasswordForm.email.trim() })
                .then(res => {
                    this.Toast.fire({ icon: 'success', title: 'Thành công', text: 'Đã gửi OTP qua Email.' });
                    this.forgotPasswordForm.step = 2;
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: err.response?.data?.message || 'Lỗi gửi OTP.' });
                })
                .finally(() => { this.forgotPasswordForm.isSending = false; });
        },
        submitResetPassword() {
            if (!this.forgotPasswordForm.otp || !this.forgotPasswordForm.newPassword) {
                this.Toast.fire({ icon: 'warning', title: 'Thiếu thông tin', text: 'Vui lòng nhập OTP và mật khẩu mới.' });
                return;
            }
            if (this.forgotPasswordForm.newPassword !== this.forgotPasswordForm.confirmPassword) {
                this.Toast.fire({ icon: 'error', title: 'Lỗi', text: 'Mật khẩu xác nhận không khớp.' });
                return;
            }
            this.forgotPasswordForm.isSending = true;
            axios.post('/api/auth/reset-password', {
                email: this.forgotPasswordForm.email.trim(),
                otp: this.forgotPasswordForm.otp.trim(),
                newPassword: this.forgotPasswordForm.newPassword
            })
                .then(res => {
                    this.Toast.fire({ icon: 'success', title: 'Thành công', text: 'Đặt lại mật khẩu thành công!' })
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
    }
};
