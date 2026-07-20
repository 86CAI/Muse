globalThis.musePlugin = {
    onShuffle(queue) {
        return queue.slice().reverse().map(song => song.id);
    }
};
