import { Notebook, NotebookCell } from '../types/notebook';

export class NotebookService {
    // private _baseUrl: string;
    private notebooks: Map<string, Notebook> = new Map();

    constructor(_baseUrl: string = '/api/notebooks') {
        // this._baseUrl = baseUrl;
    }

    async saveNotebook(notebook: Notebook): Promise<void> {
        try {
            // Update metadata
            const updatedNotebook = {
                ...notebook,
                metadata: {
                    ...notebook.metadata,
                    modified: new Date().toISOString(),
                    version: notebook.metadata.version + 1,
                },
            };

            // In a real implementation, this would make an HTTP request
            // For now, we'll simulate with local storage and in-memory cache
            this.notebooks.set(notebook.id, updatedNotebook);
            localStorage.setItem(`notebook_${notebook.id}`, JSON.stringify(updatedNotebook));

            // Simulate network delay
            await new Promise(resolve => setTimeout(resolve, 500));

            console.log('Notebook saved:', updatedNotebook.id);
        } catch (error) {
            console.error('Failed to save notebook:', error);
            throw new Error('Failed to save notebook');
        }
    }

    async loadNotebook(notebookId: string): Promise<Notebook> {
        try {
            // Check in-memory cache first
            if (this.notebooks.has(notebookId)) {
                return this.notebooks.get(notebookId)!;
            }

            // Check local storage
            const stored = localStorage.getItem(`notebook_${notebookId}`);
            if (stored) {
                const notebook = JSON.parse(stored);
                this.notebooks.set(notebookId, notebook);
                return notebook;
            }

            // In a real implementation, this would fetch from the server
            throw new Error(`Notebook ${notebookId} not found`);
        } catch (error) {
            console.error('Failed to load notebook:', error);
            throw new Error('Failed to load notebook');
        }
    }

    async shareNotebook(notebookId: string, collaborators: string[]): Promise<void> {
        try {
            const notebook = await this.loadNotebook(notebookId);

            // Update collaborators list
            const updatedNotebook = {
                ...notebook,
                collaborators,
                metadata: {
                    ...notebook.metadata,
                    modified: new Date().toISOString(),
                },
            };

            await this.saveNotebook(updatedNotebook);

            // In a real implementation, this would send notifications to collaborators
            console.log(`Notebook ${notebookId} shared with:`, collaborators);
        } catch (error) {
            console.error('Failed to share notebook:', error);
            throw new Error('Failed to share notebook');
        }
    }

    async listNotebooks(): Promise<Notebook[]> {
        try {
            // In a real implementation, this would fetch from the server
            const notebooks: Notebook[] = [];

            // Get from local storage for demo
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i);
                if (key?.startsWith('notebook_')) {
                    const stored = localStorage.getItem(key);
                    if (stored) {
                        notebooks.push(JSON.parse(stored));
                    }
                }
            }

            return notebooks.sort((a, b) =>
                new Date(b.metadata.modified).getTime() - new Date(a.metadata.modified).getTime()
            );
        } catch (error) {
            console.error('Failed to list notebooks:', error);
            throw new Error('Failed to list notebooks');
        }
    }

    async deleteNotebook(notebookId: string): Promise<void> {
        try {
            this.notebooks.delete(notebookId);
            localStorage.removeItem(`notebook_${notebookId}`);

            // Simulate network delay
            await new Promise(resolve => setTimeout(resolve, 300));

            console.log('Notebook deleted:', notebookId);
        } catch (error) {
            console.error('Failed to delete notebook:', error);
            throw new Error('Failed to delete notebook');
        }
    }

    async executeCell(cell: NotebookCell, context: any): Promise<any> {
        try {
            // Simulate cell execution with proper resource detection
            const isResourceIntensive = this.isResourceIntensive(cell.content);

            if (isResourceIntensive) {
                // Submit to GRID resources
                const jobId = await context.gridResources.submitJob(
                    cell.content,
                    { cores: 8, memory: '16GB' }
                );
                return {
                    output: `Job submitted to GRID resources: ${jobId}`,
                    jobId,
                    executedAt: new Date().toISOString(),
                };
            } else {
                // Local execution
                await new Promise(resolve => setTimeout(resolve, Math.random() * 1000 + 500));

                // Simple pattern matching for demo
                if (cell.content.includes('collision_data.getEvents')) {
                    const events = await context.collisionDataAPI.getEvents({});
                    return {
                        output: `Retrieved ${events.length} collision events`,
                        data: events,
                        executedAt: new Date().toISOString(),
                    };
                }

                if (cell.content.includes('physics_plots.createTrajectoryPlot')) {
                    return {
                        output: 'Trajectory plot created successfully',
                        plot: { type: 'trajectory', particles: 150 },
                        executedAt: new Date().toISOString(),
                    };
                }

                if (cell.content.includes('d3.')) {
                    return {
                        output: 'D3 visualization created',
                        visualization: { type: 'd3', elements: 50 },
                        executedAt: new Date().toISOString(),
                    };
                }

                return {
                    output: 'Cell executed successfully',
                    executedAt: new Date().toISOString(),
                };
            }
        } catch (error) {
            console.error('Cell execution failed:', error);
            return {
                error: error instanceof Error ? error.message : 'Unknown error',
                executedAt: new Date().toISOString(),
            };
        }
    }

    private isResourceIntensive(code: string): boolean {
        const resourceIntensivePatterns = [
            'large_dataset',
            'parallel_processing',
            'memory_intensive_operation',
            'grid_compute',
            'distributed_analysis',
        ];

        return resourceIntensivePatterns.some(pattern => code.includes(pattern)) ||
            code.length > 500;
    }

    // Version control methods
    async getNotebookVersions(notebookId: string): Promise<Notebook[]> {
        try {
            // In a real implementation, this would fetch version history from the server
            const current = await this.loadNotebook(notebookId);

            // For demo, create some mock versions
            const versions: Notebook[] = [current];

            for (let i = 1; i < current.metadata.version; i++) {
                versions.unshift({
                    ...current,
                    metadata: {
                        ...current.metadata,
                        version: i,
                        modified: new Date(Date.now() - (current.metadata.version - i) * 60000).toISOString(),
                    },
                });
            }

            return versions;
        } catch (error) {
            console.error('Failed to get notebook versions:', error);
            throw new Error('Failed to get notebook versions');
        }
    }

    async restoreNotebookVersion(notebookId: string, version: number): Promise<Notebook> {
        try {
            const versions = await this.getNotebookVersions(notebookId);
            const targetVersion = versions.find(v => v.metadata.version === version);

            if (!targetVersion) {
                throw new Error(`Version ${version} not found`);
            }

            // Create new version based on the restored one
            const restoredNotebook = {
                ...targetVersion,
                metadata: {
                    ...targetVersion.metadata,
                    version: versions[versions.length - 1].metadata.version + 1,
                    modified: new Date().toISOString(),
                },
            };

            await this.saveNotebook(restoredNotebook);
            return restoredNotebook;
        } catch (error) {
            console.error('Failed to restore notebook version:', error);
            throw new Error('Failed to restore notebook version');
        }
    }
}

export const notebookService = new NotebookService();