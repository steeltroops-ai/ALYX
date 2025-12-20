import '@testing-library/jest-dom'
import { vi } from 'vitest'

// Mock WebSocket for tests
global.WebSocket = class MockWebSocket {
    constructor(_url: string) { }
    close() { }
    send() { }
    addEventListener() { }
    removeEventListener() { }
} as any

// Mock localStorage
const localStorageMock = {
    getItem: vi.fn(),
    setItem: vi.fn(),
    removeItem: vi.fn(),
    clear: vi.fn(),
}
global.localStorage = localStorageMock as any

// Mock fetch
global.fetch = vi.fn()

// Mock monaco-editor completely
vi.mock('monaco-editor', () => ({
    default: {},
    editor: {
        create: vi.fn(() => ({
            dispose: vi.fn(),
            getValue: vi.fn(() => ''),
            setValue: vi.fn(),
            onDidChangeModelContent: vi.fn(),
            getModel: vi.fn(() => ({
                onDidChangeContent: vi.fn()
            }))
        })),
        defineTheme: vi.fn(),
        setTheme: vi.fn()
    },
    languages: {
        register: vi.fn(),
        setMonarchTokensProvider: vi.fn(),
        setLanguageConfiguration: vi.fn(),
        registerCompletionItemProvider: vi.fn(),
        CompletionItemKind: { Function: 1 },
        CompletionItemInsertTextRule: { InsertAsSnippet: 1 }
    },
    Range: class MockRange {
        constructor(_startLine: number, _startColumn: number, _endLine: number, _endColumn: number) { }
    }
}))

// Mock monaco-editor/esm/vs/editor/editor.api
vi.mock('monaco-editor/esm/vs/editor/editor.api', () => ({
    default: {},
    editor: {
        create: vi.fn(() => ({
            dispose: vi.fn(),
            getValue: vi.fn(() => ''),
            setValue: vi.fn(),
            onDidChangeModelContent: vi.fn(),
            getModel: vi.fn(() => ({
                onDidChangeContent: vi.fn()
            }))
        })),
        defineTheme: vi.fn(),
        setTheme: vi.fn()
    },
    languages: {
        register: vi.fn(),
        setMonarchTokensProvider: vi.fn(),
        setLanguageConfiguration: vi.fn(),
        registerCompletionItemProvider: vi.fn(),
        CompletionItemKind: { Function: 1 },
        CompletionItemInsertTextRule: { InsertAsSnippet: 1 }
    },
    Range: class MockRange {
        constructor(_startLine: number, _startColumn: number, _endLine: number, _endColumn: number) { }
    }
}))