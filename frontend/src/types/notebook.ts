export interface NotebookCell {
    id: string;
    type: 'code' | 'markdown';
    content: string;
    output?: any;
    executionCount?: number;
    metadata?: Record<string, any>;
}

export interface Notebook {
    id: string;
    name: string;
    cells: NotebookCell[];
    metadata: {
        kernelspec: {
            name: string;
            language: string;
        };
        language_info: {
            name: string;
            version: string;
        };
        created: string;
        modified: string;
        version: number;
    };
    collaborators?: string[];
}

export interface NotebookExecutionContext {
    collisionDataAPI: {
        getEvents: (query: any) => Promise<any[]>;
        getEventById: (id: string) => Promise<any>;
    };
    visualizationLibraries: {
        d3: any;
        customPhysicsPlots: any;
    };
    gridResources: {
        submitJob: (code: string, resources: any) => Promise<string>;
        getJobStatus: (jobId: string) => Promise<any>;
    };
}

export interface NotebookEnvironment {
    context: NotebookExecutionContext;
    isConsistent: () => boolean;
    executeCell: (cell: NotebookCell) => Promise<any>;
    saveNotebook: (notebook: Notebook) => Promise<void>;
    shareNotebook: (notebookId: string, collaborators: string[]) => Promise<void>;
}