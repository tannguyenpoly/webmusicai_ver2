// src/main/resources/static/js/modules/playlist.js
export const playlistModule = {
    data() {
        return {
            sessionPlaylist: [],
            myPlaylists: [],
            playlistTargetSong: null,
            newPlaylistForm: { name: '', isPublic: false },
            isLoadingPlaylists: false,
        };
    },
    methods: {
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
                this.Toast.fire({ icon: 'info', title: 'Bài hát đã có trong danh sách phát' });
            }
        },
        removeTrack(index) {
            this.sessionPlaylist.splice(index, 1);
            sessionStorage.setItem('music_session_playlist', JSON.stringify(this.sessionPlaylist));
            this.Toast.fire({ icon: 'warning', title: 'Đã xóa bài hát khỏi danh sách phát' });
        },
        clearPlaylist() {
            this.sessionPlaylist = [];
            sessionStorage.removeItem('music_session_playlist');
            this.Toast.fire({ icon: 'info', title: 'Đã xóa danh sách chờ phát' });
        },
        openPlaylistModal(song) {
            this.playlistTargetSong = song || null;
            this.newPlaylistForm = { name: '', isPublic: false };
            this.loadMyPlaylists();
            const element = document.getElementById('playlistManagerModal');
            if (element) bootstrap.Modal.getOrCreateInstance(element).show();
        },
        loadMyPlaylists() {
            if (!this.currentUser) return;
            this.isLoadingPlaylists = true;
            axios.get('/api/playlists/my?page=0&size=50')
                .then(res => { this.myPlaylists = res.data.content || []; })
                .finally(() => { this.isLoadingPlaylists = false; });
        },
        createPersistentPlaylist() {
            const name = (this.newPlaylistForm.name || '').trim();
            if (!name) return;
            axios.post('/api/playlists', {
                name,
                isPublic: !!this.newPlaylistForm.isPublic
            }).then(res => {
                this.myPlaylists.unshift(res.data);
                this.newPlaylistForm = { name: '', isPublic: false };
                this.Toast.fire({ icon: 'success', title: 'Đã tạo danh sách phát' });
            }).catch(err => Swal.fire('Lỗi', err.response?.data?.message || 'Không thể tạo danh sách phát', 'error'));
        },
        addSongToPersistentPlaylist(playlist) {
            if (!this.playlistTargetSong) return;
            axios.post(`/api/playlists/${playlist.id}/songs/${this.playlistTargetSong.id}`)
                .then(() => this.Toast.fire({ icon: 'success', title: `Đã thêm vào ${playlist.name}` }))
                .catch(err => Swal.fire('Lỗi', err.response?.data?.message || 'Không thể thêm bài hát', 'error'));
        },
        togglePlaylistPrivacy(playlist) {
            axios.put(`/api/playlists/${playlist.id}`, { isPublic: !playlist.isPublic })
                .then(res => {
                    playlist.isPublic = res.data.isPublic;
                    this.Toast.fire({ icon: 'success', title: playlist.isPublic ? 'Danh sách phát đã công khai' : 'Danh sách phát đã chuyển riêng tư' });
                });
        },
        deletePersistentPlaylist(playlist) {
            Swal.fire({
                title: `Xóa playlist "${playlist.name}"?`,
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Xóa',
                cancelButtonText: 'Hủy'
            }).then(result => {
                if (!result.isConfirmed) return;
                axios.delete(`/api/playlists/${playlist.id}`).then(() => {
                    this.myPlaylists = this.myPlaylists.filter(item => item.id !== playlist.id);
                });
            });
        },
    }
};
