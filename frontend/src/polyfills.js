// Polyfills for browser environment
// Some older libraries (e.g. sockjs-client) expect a `global` variable like in Node.
// Define it to avoid "ReferenceError: global is not defined".
if (typeof window !== 'undefined' && typeof window.global === 'undefined') {
  // eslint-disable-next-line no-undef
  window.global = window;
}

// Also ensure globalThis.global exists for completeness
if (typeof globalThis !== 'undefined' && typeof globalThis.global === 'undefined') {
  globalThis.global = globalThis;
}
