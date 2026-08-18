import '@testing-library/jest-dom';

Element.prototype.scrollIntoView = vi.fn();

// Node 26 exposes a process-level localStorage that is unavailable without a
// --localstorage-file flag. Give jsdom tests the browser storage API that the
// Settings page uses instead.
if (!window.localStorage) {
    const values = new Map()

    Object.defineProperty(window, 'localStorage', {
        configurable: true,
        value: {
            getItem: (key) => values.get(key) ?? null,
            setItem: (key, value) => values.set(key, String(value)),
            removeItem: (key) => values.delete(key),
            clear: () => values.clear(),
        },
    })
}
