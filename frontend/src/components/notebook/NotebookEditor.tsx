import React, { useRef, useEffect, useState } from 'react';
// import * as monaco from 'monaco-editor';

// Mock monaco for testing
const mockMonaco = {
    editor: {
        create: (_element: HTMLElement, _options: any) => ({
            dispose: () => { },
            getValue: () => '',
            setValue: (_value: string) => { },
            onDidChangeModelContent: (_callback: () => void) => ({ dispose: () => { } })
        })
    },
    languages: {
        registerCompletionItemProvider: (_language: string, _provider: any) => ({ dispose: () => { } }),
        CompletionItemKind: { Function: 1 },
        CompletionItemInsertTextRule: { InsertAsSnippet: 1 }
    },
    Range: class { constructor(_startLine: number, _startColumn: number, _endLine: number, _endColumn: number) { } }
};

const monaco = mockMonaco;
import { Box, Paper, IconButton, Typography, Chip } from '@mui/material';
import { PlayArrow, Stop, Delete, Add } from '@mui/icons-material';
import { NotebookCell, NotebookExecutionContext } from '../../types/notebook';

interface NotebookEditorProps {
    cell: NotebookCell;
    context: NotebookExecutionContext;
    onCellChange: (cell: NotebookCell) => void;
    onExecute: (cell: NotebookCell) => Promise<any>;
    onDelete: () => void;
    onAddCell: () => void;
    isExecuting?: boolean;
}

const NotebookEditor: React.FC<NotebookEditorProps> = ({
    cell,
    context: _context,
    onCellChange,
    onExecute,
    onDelete,
    onAddCell,
    isExecuting = false,
}) => {
    const editorRef = useRef<HTMLDivElement>(null);
    const [_editor, setEditor] = useState<any>(null);
    const [output, setOutput] = useState<any>(cell.output);

    useEffect(() => {
        if (editorRef.current && cell.type === 'code') {
            // Configure Monaco Editor for Python/Physics DSL
            const newEditor = monaco.editor.create(editorRef.current, {
                value: cell.content,
                language: 'python',
                theme: 'vs-dark',
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                automaticLayout: true,
                fontSize: 14,
                lineNumbers: 'on',
                wordWrap: 'on',
            });

            // Add physics-specific autocomplete
            monaco.languages.registerCompletionItemProvider('python', {
                provideCompletionItems: (_model: any, _position: any) => {
                    const suggestions = [
                        {
                            label: 'collision_data.getEvents',
                            kind: monaco.languages.CompletionItemKind.Function,
                            insertText: 'collision_data.getEvents(${1:query})',
                            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                            documentation: 'Get collision events from the database',
                            range: new monaco.Range(1, 1, 1, 1),
                        },
                        {
                            label: 'physics_plots.createTrajectoryPlot',
                            kind: monaco.languages.CompletionItemKind.Function,
                            insertText: 'physics_plots.createTrajectoryPlot(${1:data})',
                            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                            documentation: 'Create a particle trajectory visualization',
                            range: new monaco.Range(1, 1, 1, 1),
                        },
                        {
                            label: 'grid_resources.submitJob',
                            kind: monaco.languages.CompletionItemKind.Function,
                            insertText: 'grid_resources.submitJob(${1:code}, ${2:resources})',
                            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                            documentation: 'Submit computation to GRID resources',
                            range: new monaco.Range(1, 1, 1, 1),
                        },
                    ];

                    return { suggestions };
                },
            });

            newEditor.onDidChangeModelContent(() => {
                const updatedCell = {
                    ...cell,
                    content: newEditor.getValue(),
                };
                onCellChange(updatedCell);
            });

            setEditor(newEditor);

            return () => {
                newEditor.dispose();
            };
        }
    }, [cell.id, cell.type]);

    const handleExecute = async () => {
        if (cell.type === 'code') {
            try {
                const result = await onExecute(cell);
                setOutput(result);

                // Update cell with execution results
                const updatedCell = {
                    ...cell,
                    output: result,
                    executionCount: (cell.executionCount || 0) + 1,
                };
                onCellChange(updatedCell);
            } catch (error) {
                setOutput({ error: error instanceof Error ? error.message : 'Unknown error' });
            }
        }
    };

    const renderOutput = () => {
        if (!output) return null;

        if (output.error) {
            return (
                <Box sx={{ p: 2, bgcolor: 'error.dark', color: 'error.contrastText' }}>
                    <Typography variant="body2" component="pre">
                        {output.error}
                    </Typography>
                </Box>
            );
        }

        if (output.jobId) {
            return (
                <Box sx={{ p: 2, bgcolor: 'info.dark', color: 'info.contrastText' }}>
                    <Typography variant="body2">
                        Job submitted to GRID resources: {output.jobId}
                    </Typography>
                    <Typography variant="body2" component="pre">
                        {output.output}
                    </Typography>
                </Box>
            );
        }

        return (
            <Box sx={{ p: 2, bgcolor: 'grey.100', color: 'text.primary' }}>
                <Typography variant="body2" component="pre">
                    {typeof output === 'string' ? output : JSON.stringify(output, null, 2)}
                </Typography>
            </Box>
        );
    };

    if (cell.type === 'markdown') {
        return (
            <Paper sx={{ mb: 2, p: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                    <Chip label="Markdown" size="small" sx={{ mr: 1 }} />
                    <Box sx={{ flexGrow: 1 }} />
                    <IconButton size="small" onClick={onAddCell}>
                        <Add />
                    </IconButton>
                    <IconButton size="small" onClick={onDelete}>
                        <Delete />
                    </IconButton>
                </Box>
                <Typography variant="body1" component="div">
                    {cell.content}
                </Typography>
            </Paper>
        );
    }

    return (
        <Paper sx={{ mb: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', p: 1, borderBottom: 1, borderColor: 'divider' }}>
                <Chip
                    label={`Code [${cell.executionCount || ''}]`}
                    size="small"
                    color="primary"
                    sx={{ mr: 1 }}
                />
                <Box sx={{ flexGrow: 1 }} />
                <IconButton
                    size="small"
                    onClick={handleExecute}
                    disabled={isExecuting}
                    color="primary"
                >
                    {isExecuting ? <Stop /> : <PlayArrow />}
                </IconButton>
                <IconButton size="small" onClick={onAddCell}>
                    <Add />
                </IconButton>
                <IconButton size="small" onClick={onDelete}>
                    <Delete />
                </IconButton>
            </Box>

            <Box sx={{ height: 200, minHeight: 200 }}>
                <div ref={editorRef} style={{ height: '100%', width: '100%' }} />
            </Box>

            {output && (
                <Box sx={{ borderTop: 1, borderColor: 'divider' }}>
                    {renderOutput()}
                </Box>
            )}
        </Paper>
    );
};

export default NotebookEditor;