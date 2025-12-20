import React, { useRef, useEffect, useState } from 'react'
import { Box, Paper, Typography, IconButton, Tooltip } from '@mui/material'
import {
    RotateLeft,
    ZoomIn,
    ZoomOut,
    PanTool,
    Refresh,
    Fullscreen,
    FullscreenExit
} from '@mui/icons-material'
import { VisualizationEngine, CollisionEvent } from './VisualizationEngine'

interface VisualizationCanvasProps {
    events?: CollisionEvent[]
    selectedEventId?: string
    height?: number
}

const VisualizationCanvas: React.FC<VisualizationCanvasProps> = ({
    events = [],
    selectedEventId,
    height = 600
}) => {
    const containerRef = useRef<HTMLDivElement>(null)
    const engineRef = useRef<VisualizationEngine | null>(null)
    const [isFullscreen, setIsFullscreen] = useState(false)
    const [isInteracting, setIsInteracting] = useState(false)
    const [interactionMode, setInteractionMode] = useState<'rotate' | 'pan' | 'zoom'>('rotate')

    // Initialize visualization engine
    useEffect(() => {
        if (containerRef.current && !engineRef.current) {
            try {
                engineRef.current = new VisualizationEngine(containerRef.current)
            } catch (error) {
                console.error('Failed to initialize visualization engine:', error)
            }
        }

        return () => {
            if (engineRef.current) {
                engineRef.current.dispose()
                engineRef.current = null
            }
        }
    }, [])

    // Load events when they change
    useEffect(() => {
        if (engineRef.current && events.length > 0) {
            engineRef.current.loadMultipleEvents(events)
        }
    }, [events])

    // Handle window resize
    useEffect(() => {
        const handleResize = () => {
            if (engineRef.current && containerRef.current) {
                const { clientWidth, clientHeight } = containerRef.current
                engineRef.current.resize(clientWidth, clientHeight)
            }
        }

        window.addEventListener('resize', handleResize)
        return () => window.removeEventListener('resize', handleResize)
    }, [])

    // Mouse interaction handlers
    const handleMouseDown = (event: React.MouseEvent) => {
        setIsInteracting(true)
        event.preventDefault()
    }

    const handleMouseMove = (event: React.MouseEvent) => {
        if (!isInteracting || !engineRef.current) return

        const deltaX = event.movementX
        const deltaY = event.movementY

        let interactionEvent
        switch (interactionMode) {
            case 'rotate':
                interactionEvent = {
                    type: 'rotation' as const,
                    deltaX,
                    deltaY
                }
                break
            case 'pan':
                interactionEvent = {
                    type: 'pan' as const,
                    deltaX,
                    deltaY
                }
                break
            default:
                return
        }

        engineRef.current.handleInteraction(interactionEvent)
    }

    const handleMouseUp = () => {
        setIsInteracting(false)
    }

    const handleWheel = (event: React.WheelEvent) => {
        if (!engineRef.current) return

        event.preventDefault()
        const zoomFactor = event.deltaY > 0 ? 1.1 : 0.9

        engineRef.current.handleInteraction({
            type: 'zoom',
            zoomFactor
        })
    }

    // Control handlers
    const handleReset = () => {
        if (engineRef.current) {
            engineRef.current.clearAllEvents()
            if (events.length > 0) {
                engineRef.current.loadMultipleEvents(events)
            }
        }
    }

    const handleZoomIn = () => {
        if (engineRef.current) {
            engineRef.current.handleInteraction({
                type: 'zoom',
                zoomFactor: 0.8
            })
        }
    }

    const handleZoomOut = () => {
        if (engineRef.current) {
            engineRef.current.handleInteraction({
                type: 'zoom',
                zoomFactor: 1.2
            })
        }
    }

    const toggleFullscreen = () => {
        setIsFullscreen(!isFullscreen)
    }

    return (
        <Paper
            elevation={3}
            sx={{
                position: isFullscreen ? 'fixed' : 'relative',
                top: isFullscreen ? 0 : 'auto',
                left: isFullscreen ? 0 : 'auto',
                width: isFullscreen ? '100vw' : '100%',
                height: isFullscreen ? '100vh' : height,
                zIndex: isFullscreen ? 9999 : 'auto',
                overflow: 'hidden'
            }}
        >
            {/* Controls */}
            <Box
                sx={{
                    position: 'absolute',
                    top: 8,
                    right: 8,
                    zIndex: 10,
                    display: 'flex',
                    gap: 1,
                    backgroundColor: 'rgba(0, 0, 0, 0.7)',
                    borderRadius: 1,
                    padding: 0.5
                }}
            >
                <Tooltip title="Rotate Mode">
                    <IconButton
                        size="small"
                        onClick={() => setInteractionMode('rotate')}
                        color={interactionMode === 'rotate' ? 'primary' : 'default'}
                        sx={{ color: 'white' }}
                    >
                        <RotateLeft />
                    </IconButton>
                </Tooltip>

                <Tooltip title="Pan Mode">
                    <IconButton
                        size="small"
                        onClick={() => setInteractionMode('pan')}
                        color={interactionMode === 'pan' ? 'primary' : 'default'}
                        sx={{ color: 'white' }}
                    >
                        <PanTool />
                    </IconButton>
                </Tooltip>

                <Tooltip title="Zoom In">
                    <IconButton
                        size="small"
                        onClick={handleZoomIn}
                        sx={{ color: 'white' }}
                    >
                        <ZoomIn />
                    </IconButton>
                </Tooltip>

                <Tooltip title="Zoom Out">
                    <IconButton
                        size="small"
                        onClick={handleZoomOut}
                        sx={{ color: 'white' }}
                    >
                        <ZoomOut />
                    </IconButton>
                </Tooltip>

                <Tooltip title="Reset View">
                    <IconButton
                        size="small"
                        onClick={handleReset}
                        sx={{ color: 'white' }}
                    >
                        <Refresh />
                    </IconButton>
                </Tooltip>

                <Tooltip title={isFullscreen ? "Exit Fullscreen" : "Fullscreen"}>
                    <IconButton
                        size="small"
                        onClick={toggleFullscreen}
                        sx={{ color: 'white' }}
                    >
                        {isFullscreen ? <FullscreenExit /> : <Fullscreen />}
                    </IconButton>
                </Tooltip>
            </Box>

            {/* Info Panel */}
            <Box
                sx={{
                    position: 'absolute',
                    top: 8,
                    left: 8,
                    zIndex: 10,
                    backgroundColor: 'rgba(0, 0, 0, 0.7)',
                    borderRadius: 1,
                    padding: 1,
                    color: 'white',
                    minWidth: 200
                }}
            >
                <Typography variant="caption" display="block">
                    Events Loaded: {events.length}
                </Typography>
                <Typography variant="caption" display="block">
                    Mode: {interactionMode.charAt(0).toUpperCase() + interactionMode.slice(1)}
                </Typography>
                {selectedEventId && (
                    <Typography variant="caption" display="block">
                        Selected: {selectedEventId.slice(0, 8)}...
                    </Typography>
                )}
            </Box>

            {/* Canvas Container */}
            <Box
                ref={containerRef}
                sx={{
                    width: '100%',
                    height: '100%',
                    cursor: isInteracting
                        ? (interactionMode === 'rotate' ? 'grabbing' : 'move')
                        : (interactionMode === 'rotate' ? 'grab' : 'crosshair'),
                    userSelect: 'none'
                }}
                onMouseDown={handleMouseDown}
                onMouseMove={handleMouseMove}
                onMouseUp={handleMouseUp}
                onMouseLeave={handleMouseUp}
                onWheel={handleWheel}
            />
        </Paper>
    )
}

export default VisualizationCanvas