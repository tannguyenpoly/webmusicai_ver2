// src/main/resources/static/js/modules/globalData.js
export const globalDataModule = {
    data() {
        return {
            isDarkMode: localStorage.getItem('music_theme') !== 'light',
            currentPage: window.location.pathname,
            Toast: null, // Sẽ được khởi tạo trong mounted của app.js
            
            // Filters và sort options cho workspace/profile songs
            workspaceFilters: {
                liked: false,
                public: false,
                private: false,
                pending: false
            },
            workspaceSortOption: 'newest',
            sortLabels: {
                newest: 'Mới nhất',
                oldest: 'Cũ nhất',
                most_liked: 'Được thích nhiều nhất',
                least_liked: 'Được thích ít nhất'
            },
        };
    },
    computed: {
        activeFiltersCount() {
            let count = 0;
            if (this.workspaceFilters.liked) count++;
            if (this.workspaceFilters.public) count++;
            if (this.workspaceFilters.private) count++;
            if (this.workspaceFilters.pending) count++;
            return count;
        },
        filteredProfileSongs() {
            let result = [...this.profileGeneratedSongs]; // profileGeneratedSongs từ userProfileModule
            if (this.filters.keyword && this.filters.keyword.trim() !== '') { // filters từ exploreModule
                const kw = this.filters.keyword.toLowerCase();
                result = result.filter(s =>
                    (s.title && s.title.toLowerCase().includes(kw)) ||
                    (s.prompt && s.prompt.toLowerCase().includes(kw))
                );
            }

            const activeOptions = [];
            if (this.workspaceFilters.public) activeOptions.push('PUBLIC');
            if (this.workspaceFilters.private) activeOptions.push('PRIVATE');

            if (activeOptions.length > 0) {
                result = result.filter(s => activeOptions.includes(
                    s.isPublic || s.is_public ? 'PUBLIC' : 'PRIVATE'));
            }

            if (this.workspaceFilters.pending) {
                result = result.filter(s => s.status === 'PENDING');
            }

            if (this.workspaceFilters.liked) {
                result = result.filter(s => this.profileFavoriteSongs.some(fav => // profileFavoriteSongs từ userProfileModule
                    fav.id === s.id || (fav.song && fav.song.id === s.id) || fav.songId === s.id));
            }

            if (this.workspaceSortOption === 'newest') {
                result.sort((a, b) => new Date(b.created_at || b.createdAt || 0) - new Date(a.created_at || a.createdAt || 0));
            } else if (this.workspaceSortOption === 'oldest') {
                result.sort((a, b) => new Date(a.created_at || a.createdAt || 0) - new Date(b.created_at || b.createdAt || 0));
            } else if (this.workspaceSortOption === 'most_liked') {
                const getLikes = (song) => song.total_likes || song.totalLikes || 0;
                result.sort((a, b) => getLikes(b) - getLikes(a));
            } else if (this.workspaceSortOption === 'least_liked') {
                const getLikes = (song) => song.total_likes || song.totalLikes || 0;
                result.sort((a, b) => getLikes(a) - getLikes(b));
            }

            return result;
        }
    },
    methods: {
        // Các phương thức tương tác với globalData hoặc điều phối giữa các module
        toggleFilterOption(option) {
            if (option === 'public') {
                this.workspaceFilters.public = !this.workspaceFilters.public;
                if (this.workspaceFilters.public) this.workspaceFilters.private = false;
            } else if (option === 'private') {
                this.workspaceFilters.private = !this.workspaceFilters.private;
                if (this.workspaceFilters.private) this.workspaceFilters.public = false;
            } else {
                this.workspaceFilters[option] = !this.workspaceFilters[option];
            }
        },
        resetWorkspaceFilters() {
            this.workspaceFilters.liked = false;
            this.workspaceFilters.public = false;
            this.workspaceFilters.private = false;
            this.workspaceFilters.pending = false;
        },
    }
};
