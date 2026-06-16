new Vue({
    el: '#app',
    data: {
        // QUẢN LÝ THEO DÕI CHẾ ĐỘ SÁNG / TỐI ĐỒNG BỘ LAYOUT VÀ INDEX
        isDarkMode: localStorage.getItem('music_theme') !== 'light',

        currentUser: null,           // Gán null ban đầu để ẩn giao diện tài khoản khi chưa login
        userTokens: 0,               // Token thực tế từ database
        publicSongs: [],             // Danh sách nhạc public dạng mảng phẳng
        sessionPlaylist: [],         // Playlist tạm thời lưu trong Session Storage của Guest

        generationForm: {
            username: '',
            prompt: '',
            instrumental: true
        },
        isGenerating: false,

        currentTrack: { id: null, title: '', prompt: '', status: '', audioUrl: '' },

        // Cấu trúc Form đăng nhập đồng bộ chính xác với v-model
        loginForm: {
            username: '',
            password: ''
        },

        registerForm: {
            username: '',
            fullname: '',
            email: '',
            password: '',
            confirmPassword: ''
        },
        filters: { keyword: '' },
        pollingTimer: null,

        // Trạng thái hiển thị modal và dữ liệu form Hồ sơ
        profileModalTab: 'info', // 'info' hoặc 'password'
        showProfileModal: false,
        profileForm: {
            fullname: '',
            email: '',
            photo: ''
        },

        // Form đổi mật khẩu
        changePasswordForm: {
            oldPassword: '',
            newPassword: '',
            confirmNewPassword: ''
        }
    },
    computed: {
        filteredSongs() {
            let result = [...this.publicSongs];
            if (this.filters.keyword && this.filters.keyword.trim() !== '') {
                const kw = this.filters.keyword.toLowerCase();
                result = result.filter(s =>
                    (s.title && s.title.toLowerCase().includes(kw)) ||
                    (s.prompt && s.prompt.toLowerCase().includes(kw))
                );
            }
            return result;
        }
    },
    mounted() {
        // Khởi tạo Toast cấu hình SweetAlert2 mịn ở góc màn hình
        this.Toast = Swal.mixin({
            toast: true,
            position: 'top-end',
            showConfirmButton: false,
            timer: 3000,
            timerProgressBar: true,
            didOpen: (toast) => {
                toast.addEventListener('mouseenter', Swal.stopTimer)
                toast.addEventListener('mouseleave', Swal.resumeTimer)
            }
        });

        // Luôn kiểm tra session backend làm nguồn tin cậy duy nhất khi tải trang
        axios.get('/api/users/auth-session')
            .then(response => {
                // Người dùng đã đăng nhập
                const userData = response.data;
                this.currentUser = userData.username;
                this.generationForm.username = userData.username;
                this.userTokens = userData.token_balance;
                localStorage.setItem('music_username', userData.username); // Đồng bộ lại localStorage
            })
            .catch(() => {
                // Người dùng chưa đăng nhập hoặc session hết hạn
                this.currentUser = null;
                localStorage.removeItem('music_username'); // Dọn dẹp localStorage cũ
            })
            .finally(() => {
                // Tải các tài nguyên công khai sau khi đã xác định trạng thái đăng nhập
                this.loadPublicSongs();
                this.loadSessionPlaylist();
            });
    },
    methods: {
        // HÀM CHUYỂN ĐỔI CHẾ ĐỘ SÁNG / TỐI ĐỒNG BỘ TOÀN HỆ THỐNG
        toggleTheme() {
            this.isDarkMode = !this.isDarkMode;
            const currentTheme = this.isDarkMode ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', currentTheme);
            localStorage.setItem('music_theme', currentTheme);
        },

        loadPublicSongs() {
            axios.get('/api/songs/public')
                .then(response => {
                    this.publicSongs = Array.isArray(response.data) ? response.data : [];
                })
                .catch(error => {
                    console.error("Lỗi khi tải kho nhạc public:", error);
                });
        },

        generateMusic() {
            if (!this.generationForm.prompt.trim()) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Thiếu thông tin',
                    text: 'Vui lòng nhập mô tả ý tưởng để AI tạo giai điệu!',
                    confirmButtonColor: '#16a34a'
                });
                return;
            }

            this.isGenerating = true;
            this.generationForm.username = this.currentUser;

            axios.post('/api/songs/generate', this.generationForm)
                .then(response => {
                    const data = response.data;
                    this.Toast.fire({ icon: 'success', title: 'AI đang xử lý giai điệu ngầm...' });
                    this.userTokens = data.remaining_tokens;

                    this.currentTrack = {
                        id: data.songId,
                        title: "AI đang tiến hành xử lý bài hát...",
                        prompt: this.generationForm.prompt,
                        status: "PENDING",
                        audioUrl: ""
                    };

                    this.generationForm.prompt = '';
                    this.isGenerating = false;
                    this.startPollingStatus(data.songId);
                })
                .catch(error => {
                    this.isGenerating = false;
                    Swal.fire({
                        icon: 'error',
                        title: 'Thất bại',
                        text: error.response ? error.response.data : 'Lỗi kết nối lõi AI.',
                        confirmButtonColor: '#dc3545'
                    });
                });
        },

        startPollingStatus(songId) {
            if (this.pollingTimer) clearInterval(this.pollingTimer);

            this.pollingTimer = setInterval(() => {
                axios.get(`/api/songs/${songId}/status`)
                    .then(response => {
                        const statusData = response.data;
                        if (this.currentTrack.id === songId) {
                            this.currentTrack.status = statusData.status;
                        }

                        if (statusData.status === 'COMPLETED') {
                            clearInterval(this.pollingTimer);
                            this.currentTrack.title = statusData.title;
                            this.currentTrack.audioUrl = statusData.audio_url;
                            this.loadPublicSongs();

                            this.Toast.fire({ icon: 'success', title: `Sinh xong bài: ${statusData.title}!` });

                            this.$nextTick(() => {
                                const audio = document.getElementById('audio-element');
                                if (audio) { audio.load(); audio.play(); }
                            });
                        } else if (statusData.status === 'FAILED') {
                            clearInterval(this.pollingTimer);
                            Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Quá trình tạo nhạc thất bại!' });
                        }
                    })
                    .catch(() => {
                        clearInterval(this.pollingTimer);
                    });
            }, 3000);
        },

        playTrack(song) {
            if (this.pollingTimer && this.currentTrack.id === song.id && this.currentTrack.status === 'PENDING') return;
            if (this.pollingTimer) clearInterval(this.pollingTimer);

            this.currentTrack = {
                id: song.id,
                title: song.title,
                prompt: song.prompt,
                status: 'COMPLETED',
                audioUrl: song.audioUrl
            };

            this.$nextTick(() => {
                const audio = document.getElementById('audio-element');
                if (audio) { audio.load(); audio.play(); }
            });
        },

        loadSessionPlaylist() {
            const data = sessionStorage.getItem('music_session_playlist');
            this.sessionPlaylist = data ? JSON.parse(data) : [];
        },
        addToPlaylist(song) {
            const isExist = this.sessionPlaylist.some(item => item.id === song.id);
            if (!isExist) {
                this.sessionPlaylist.push(song);
                sessionStorage.setItem('music_session_playlist', JSON.stringify(this.sessionPlaylist));
                this.Toast.fire({ icon: 'success', title: 'Đã thêm vào danh sách phát tạm' });
            } else {
                this.Toast.fire({ icon: 'info', title: 'Bài hát đã tồn tại trong playlist' });
            }
        },
        removeTrack(index) {
            this.sessionPlaylist.splice(index, 1);
            sessionStorage.setItem('music_session_playlist', JSON.stringify(this.sessionPlaylist));
            this.Toast.fire({ icon: 'warning', title: 'Đã xóa bài hát khỏi playlist' });
        },
        clearPlaylist() {
            this.sessionPlaylist = [];
            sessionStorage.removeItem('music_session_playlist');
            this.Toast.fire({ icon: 'error', title: 'Đã giải phóng danh sách phát' });
        },

        handleLogin() {
            const btn = document.getElementById('submit-btn');
            if (btn) {
                btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang kết nối...';
                btn.disabled = true;
            }

            axios.post('/api/auth/login', this.loginForm)
                .then(response => {
                    localStorage.setItem('music_username', this.loginForm.username);

                    if (btn) {
                        btn.innerHTML = '<i class="ti ti-check"></i> Kích hoạt thành công!';
                        btn.style.background = '#15803d';
                    }

                    this.Toast.fire({
                        icon: 'success',
                        title: `Khởi động hệ thống thành công! Chào mừng ${this.loginForm.username}.`
                    });

                    setTimeout(() => {
                        window.location.href = '/';
                    }, 1000);
                })
                .catch(() => {
                    if (btn) {
                        btn.innerHTML = '<i class="ti ti-bolt"></i> Kích hoạt hệ thống';
                        btn.disabled = false;
                    }
                    Swal.fire({
                        icon: 'error',
                        title: 'Đăng nhập thất bại',
                        text: 'Tài khoản hoặc mật khẩu không chính xác.',
                        confirmButtonColor: '#16a34a'
                    });
                });
        },

        handleRegister() {
            if (this.registerForm.password !== this.registerForm.confirmPassword) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Mật khẩu xác nhận không trùng khớp!' });
                return;
            }

            const submitData = {
                username: this.registerForm.username,
                fullname: this.registerForm.fullname,
                email: this.registerForm.email,
                password: this.registerForm.password
            };

            axios.post('/api/auth/register', submitData)
                .then(() => {
                    Swal.fire({
                        icon: 'success',
                        title: 'Thành công',
                        text: 'Tạo tài khoản thành công! Bạn nhận được 5 Token trải nghiệm.',
                        confirmButtonColor: '#16a34a'
                    }).then(() => {
                        window.location.href = '/login';
                    });
                })
                .catch(error => {
                    Swal.fire({
                        icon: 'error',
                        title: 'Lỗi',
                        text: error.response ? "Đăng ký thất bại: " + error.response.data : "Tài khoản hoặc Email đã tồn tại."
                    });
                });
        },

        handleLogout(showConfirm = true) {
            if (!showConfirm) {
                // Ép đăng xuất ngay lập tức không cần hỏi (Dùng sau khi đổi mật khẩu)
                axios.post('/logout').finally(() => {
                    localStorage.removeItem('music_username');
                    this.currentUser = null;
                    this.userTokens = 0;
                    this.generationForm.username = '';
                    window.location.href = '/'; 
                });
                return;
            }

            Swal.fire({
                title: 'Xác nhận đăng xuất?',
                text: "Hệ thống sẽ ngắt kết nối với tài khoản hiện tại.",
                icon: 'question',
                showCancelButton: true,
                confirmButtonColor: '#16a34a',
                cancelButtonColor: '#d33',
                confirmButtonText: 'Đăng xuất',
                cancelButtonText: 'Hủy'
            }).then((result) => {
                if (result.isConfirmed) {
                    // Gọi API logout của Spring Security để hủy session phía backend
                    axios.post('/logout').finally(() => {
                        // Dọn dẹp localStorage và trạng thái Vue sau khi gọi API
                        localStorage.removeItem('music_username');
                        this.currentUser = null;
                        this.userTokens = 0;
                        this.generationForm.username = '';
                        window.location.href = '/'; // Tải lại trang chủ
                    });
                }
            });
        },

        // --- CÁC HÀM QUẢN LÝ HỒ SƠ CÁ NHÂN ---
        openProfileModal() {
            // Reset về trạng thái mặc định mỗi khi mở
            this.profileModalTab = 'info';
            this.changePasswordForm = { oldPassword: '', newPassword: '', confirmNewPassword: '' };

            if (!this.currentUser) return;
            axios.get(`/api/users/${this.currentUser}/profile`)
                .then(response => {
                    const data = response.data;
                    this.profileForm.fullname = data.fullname || '';
                    this.profileForm.email = data.email || '';
                    this.profileForm.photo = data.photo || '';
                    this.showProfileModal = true;
                })
                .catch(error => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Không thể tải thông tin cá nhân' });
                });
        },

        closeProfileModal() {
            this.showProfileModal = false;
        },

        submitUpdateProfile() {
            axios.put(`/api/users/${this.currentUser}/profile`, this.profileForm)
                .then(response => {
                    this.Toast.fire({ icon: 'success', title: 'Cập nhật hồ sơ thành công!' });
                    this.showProfileModal = false;
                })
                .catch(error => {
                    Swal.fire({
                        icon: 'error',
                        title: 'Cập nhật thất bại',
                        text: error.response && error.response.data ? (error.response.data.message || 'Lỗi dữ liệu') : 'Vui lòng kiểm tra lại thông tin.'
                    });
                });
        },

        submitChangePassword() {
            if (this.changePasswordForm.newPassword !== this.changePasswordForm.confirmNewPassword) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Mật khẩu mới và mật khẩu xác nhận không trùng khớp!' });
                return;
            }

            const payload = {
                oldPassword: this.changePasswordForm.oldPassword,
                newPassword: this.changePasswordForm.newPassword
            };

            axios.put(`/api/users/${this.currentUser}/change-password`, payload)
                .then(response => {
                    this.showProfileModal = false; // Đóng popup modal ngay lập tức
                    Swal.fire({
                        icon: 'success',
                        title: 'Thành công!',
                        text: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại.',
                        confirmButtonColor: '#16a34a'
                    }).then(() => {
                        // Gọi logout để xóa session và yêu cầu đăng nhập lại
                        this.handleLogout(false); // false để không hiện popup hỏi
                    });
                })
                .catch(error => {
                    Swal.fire({
                        icon: 'error',
                        title: 'Đổi mật khẩu thất bại',
                        text: error.response && error.response.data ? error.response.data.message : 'Đã có lỗi xảy ra.'
                    });
                });
        }
    }
});