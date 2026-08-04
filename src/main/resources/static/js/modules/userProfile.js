// src/main/resources/static/js/modules/userProfile.js
export const userProfileModule = {
    data() {
        return {
            currentUser: null,
            isGuest: false,
            guestUsername: null,
            userPhoto: null,
            isAdmin: false,
            userTokens: 0,
            userTier: 'FREE',
            userTierExpiresAt: null,
            authProvider: 'LOCAL',
            hasLocalPassword: true,

            profileModalTab: 'info',
            showProfileModal: false,
            profileModalError: '',
            profileForm: { fullname: '', email: '', photo: '', authProvider: 'LOCAL' },
            changePasswordForm: { oldPassword: '', newPassword: '', confirmNewPassword: '' },
            passwordResetMode: false,

            profilePageData: {},
            profileStats: { total: 0, completed: 0, pending: 0, totalFavorites: 0 },
            profileTab: 'generated',
            profileGeneratedSongs: [],
            profileFavoriteSongs: [],
            isLoadingProfileSongs: false,
            isLoadingProfileFav: false,
            favoriteSongs: [], // Thêm thuộc tính này
            isLoadingFavorites: false, // Thêm thuộc tính này
            profileSongPagination: { page: 0, size: 10, hasMore: false },
        };
    },
    computed: {
        userTierLabel() {
            const labels = {
                FREE: 'Miễn phí',
                CREATOR: 'Nhà sáng tạo',
                PRO: 'Chuyên nghiệp',
                STUDIO: 'Phòng thu'
            };
            return labels[this.userTier] || this.userTier || 'Miễn phí';
        },
    },
    methods: {
        migrateGuestSongs(guestId, username) {
            if (!guestId || !username) return;
            axios.post(`/api/auth/migrate?guestId=${guestId}&username=${username}`)
                .then(response => {
                    console.log("Đã chuyển quyền sở hữu nhạc:", response.data.message);
                    localStorage.removeItem('music_guest_id');
                })
                .catch(error => {
                    console.error("Lỗi chuyển quyền sở hữu nhạc:", error);
                });
        },
        loadUserTokenBalance(username) {
            axios.get(`/api/users/${username}/profile`)
                .then(response => {
                    if (response.data) {
                        if (response.data.token_balance !== undefined) this.userTokens = response.data.token_balance;
                        if (response.data.photo) this.userPhoto = response.data.photo;
                        this.userTier = response.data.accountTier || 'FREE';
                        this.userTierExpiresAt = response.data.tierExpiresAt || null;
                        this.authProvider = response.data.authProvider || 'LOCAL';
                        this.hasLocalPassword = response.data.hasLocalPassword !== false;
                    }
                })
                .catch(error => {
                    console.error("Lỗi tải thông tin số dư token:", error);
                    if (this.isGuest) {
                        this.userTokens = 5;
                    }
                });
        },
        triggerAvatarUpload() {
            if (this.$refs.avatarFileInput) this.$refs.avatarFileInput.click();
            else {
                const elem = document.getElementById('avatarFileInputHidden');
                if (elem) elem.click();
            }
        },
        uploadAvatarFile(event) {
            const file = event.target.files[0];
            if (!file) return;
            if (!file.type.startsWith('image/')) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng chọn file hình ảnh (.jpg, .png, .webp, .gif)!' });
                return;
            }
            if (file.size > 5 * 1024 * 1024) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Dung lượng ảnh tối đa là 5MB!' });
                return;
            }
            const formData = new FormData();
            formData.append('file', file);
            Swal.fire({
                title: 'Đang tải ảnh lên...',
                text: 'Vui lòng chờ trong giây lát',
                allowOutsideClick: false,
                didOpen: () => { Swal.showLoading(); }
            });
            axios.post(`/api/users/${this.currentUser}/avatar`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            })
                .then(res => {
                    const newPhoto = res.data.photo;
                    this.userPhoto = newPhoto;
                    if (this.profilePageData) this.profilePageData.photo = newPhoto;
                    if (this.profileForm) this.profileForm.photo = newPhoto;
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tải ảnh đại diện mới thành công!' });
                })
                .catch(err => {
                    let msg = 'Tải ảnh đại diện thất bại!';
                    if (err.response && err.response.data && err.response.data.message) msg = err.response.data.message;
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: msg });
                });
        },
        loadProfilePageData() {
            axios.get(`/api/users/${this.profileUsername}/profile`)
                .then(res => {
                    this.profilePageData = res.data;
                    if (res.data.total_songs !== undefined) {
                        this.profileStats.total = res.data.total_songs;
                        this.profileStats.completed = res.data.completed_songs;
                        this.profileStats.pending = res.data.pending_songs;
                    }
                    if (res.data.total_favorites !== undefined) this.profileStats.totalFavorites = res.data.total_favorites;
                })
                .catch(err => console.error(err));
        },
        loadProfileGeneratedSongs(loadMore = false) {
            if (!loadMore) {
                this.profileSongPagination.page = 0;
                this.profileGeneratedSongs = [];
            }
            this.isLoadingProfileSongs = true;
            axios.get(`/api/users/${this.profileUsername}/songs?page=${this.profileSongPagination.page}&size=${this.profileSongPagination.size}`)
                .then(res => {
                    const data = res.data;
                    const content = data.content ? data.content : Array.isArray(data) ? data : [];
                    if (loadMore) this.profileGeneratedSongs = this.profileGeneratedSongs.concat(content);
                    else this.profileGeneratedSongs = content;

                    if (data.content) {
                        this.profileSongPagination.hasMore = !data.last;
                        if (this.profileStats.total === 0 || !loadMore) this.profileStats.total = data.totalElements;
                    } else {
                        this.profileSongPagination.hasMore = false;
                        this.profileStats.total = this.profileGeneratedSongs.length;
                    }
                })
                .catch(err => console.error(err))
                .finally(() => { this.isLoadingProfileSongs = false; });
        },
        loadMoreProfileSongs() {
            this.profileSongPagination.page++;
            this.loadProfileGeneratedSongs(true);
        },
        switchToFavTab() {
            this.profileTab = 'favorites';
            if (this.profileFavoriteSongs.length === 0) this.loadProfileFavorites();
        },
        loadProfileFavorites() {
            this.isLoadingProfileFav = true;
            axios.get('/api/songs/my-favorites')
                .then(res => {
                    this.profileFavoriteSongs = Array.isArray(res.data) ? res.data : [];
                    this.profileStats.totalFavorites = this.profileFavoriteSongs.length;
                })
                .catch(err => console.error(err))
                .finally(() => { this.isLoadingProfileFav = false; });
        },
        toggleProfileSongVisibility(song) {
            axios.put(`/api/songs/${song.id}/visibility`)
                .then(res => {
                    song.isPublic = res.data.isPublic !== undefined ? res.data.isPublic : !song.isPublic;
                    this.Toast.fire({ icon: 'success', title: song.isPublic ? 'Đã công khai bài hát' : 'Đã chuyển thành riêng tư' });
                })
                .catch(err => {
                    Swal.fire('Lỗi', 'Không thể đổi trạng thái bài hát', 'error');
                });
        },
        deleteGeneratedSong(song) {
            Swal.fire({
                title: 'Xác nhận xóa?',
                text: "Bài nhạc này sẽ bị xóa vĩnh viễn khỏi hệ thống!",
                icon: 'warning',
                showCancelButton: true,
                confirmButtonColor: '#dc3545',
                cancelButtonText: 'Hủy',
                confirmButtonText: 'Xóa ngay'
            }).then(result => {
                if (result.isConfirmed) {
                    axios.delete(`/api/songs/${song.id}`)
                        .then(() => {
                            this.profileGeneratedSongs = this.profileGeneratedSongs.filter(s => s.id !== song.id);
                            this.profileStats.total--;
                            if (song.status === 'COMPLETED') this.profileStats.completed--;
                            if (song.status === 'PENDING') this.profileStats.pending--;
                            this.Toast.fire({ icon: 'success', title: 'Đã xóa bài nhạc thành công.' });
                        })
                        .catch(err => Swal.fire('Lỗi', 'Không thể xóa bài nhạc.', 'error'));
                }
            });
        },
        removeFavAndUpdate(song) {
            axios.post(`/api/songs/${song.id}/like`)
                .then(res => {
                    this.profileFavoriteSongs = this.profileFavoriteSongs.filter(s => s.id !== song.id);
                    if (this.profileStats.totalFavorites > 0) this.profileStats.totalFavorites--;
                    this.Toast.fire({ icon: 'success', title: 'Đã bỏ yêu thích bài hát.' });
                })
                .catch(err => this.Toast.fire({ icon: 'error', title: 'Lỗi xử lý.' }));
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
        loadFavoriteSongs() { // Thêm phương thức này
            if (!this.currentUser) { window.location.href = '/login'; return; }
            this.isLoadingFavorites = true;
            axios.get('/api/songs/my-favorites')
                .then(response => { this.favoriteSongs = Array.isArray(response.data) ? response.data : []; })
                .catch(error => { this.Toast.fire({ icon: 'error', title: 'Không thể tải danh sách yêu thích.' }); })
                .finally(() => { this.isLoadingFavorites = false; });
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
        beginProfilePasswordReset() {
            if (!this.profileForm.email) {
                Swal.fire({ icon: 'warning', title: 'Thiếu email', text: 'Hãy cập nhật email trong hồ sơ trước.' });
                return;
            }
            this.passwordResetMode = true;
            this.forgotPasswordForm = {
                email: this.profileForm.email,
                otp: '',
                newPassword: '',
                confirmPassword: '',
                step: 1,
                isSending: false
            };
        },
    }
};
