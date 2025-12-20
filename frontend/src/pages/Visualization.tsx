import React, { useState, useEffect } from 'react'
import {
    Typography,
    Box,
    Grid,
    Card,
    CardContent,
    Button,
    Chip,
    Stack,
    Alert
} from '@mui/material'
import { PlayArrow, Pause, SkipNext, SkipPrevious } from '@mui/icons-material'
import VisualizationCanvas from '../components/visualization/VisualizationCanvas'
import { CollisionEvent } from '../components/visualization/VisualizationEngine'

const Visualization: React.FC = () => {
    const [events, setEvents] = useState<CollisionEvent[]>([])
    const [selectedEventId, setSelectedEventId] = useState<string>('')
    const [currentEventIndex, setCurrentEventIndex] = useState(0)
    const [isPlaying, setIsPlaying] = useState(false)
    const [playbackSpeed] = useState(1000) // ms between events

    // Generate sample collision events for demonstration
    useEffect(() => {
        const generateSampleEvents = (): CollisionEvent[] => {
            const sampleEvents: CollisionEvent[] = []

            for (let i = 0; i < 5; i++) {
                const event: CollisionEvent = {
                    eventId: `event-${i + 1}`,
                    particleCount: Math.floor(Math.random() * 20) + 5,
                    detectorHits: [],
                    particleTracks: []
                }

                // Generate detector hits
                for (let j = 0; j < event.particleCount; j++) {
                    event.detectorHits.push({
                        x: (Math.random() - 0.5) * 80,
                        y: (Math.random() - 0.5) * 80,
                        z: (Math.random() - 0.5) * 80,
                        energy: Math.random() * 50 + 1
                    })
                }

                // Generate particle tracks
                const trackCount = Math.floor(event.particleCount / 3)
                for (let k = 0; k < trackCount; k++) {
                    const pointCount = Math.floor(Math.random() * 8) + 3
                    const points = []

                    // Create a curved trajectory
                    const startX = (Math.random() - 0.5) * 20
                    const startY = (Math.random() - 0.5) * 20
                    const startZ = (Math.random() - 0.5) * 20

                    for (let p = 0; p < pointCount; p++) {
                        const t = p / (pointCount - 1)
                        points.push({
                            x: startX + t * 40 * (Math.random() - 0.5),
                            y: startY + t * 40 * (Math.random() - 0.5),
                            z: startZ + t * 40 * (Math.random() - 0.5)
                        })
                    }

                    event.particleTracks.push({
                        points,
                        momentum: Math.random() * 10 + 1,
                        charge: Math.floor(Math.random() * 3) - 1 // -1, 0, or 1
                    })
                }

                sampleEvents.push(event)
            }

            return sampleEvents
        }

        const sampleEvents = generateSampleEvents()
        setEvents(sampleEvents)
        if (sampleEvents.length > 0) {
            setSelectedEventId(sampleEvents[0].eventId)
        }
    }, [])

    // Animation playback
    useEffect(() => {
        if (!isPlaying || events.length === 0) return

        const interval = setInterval(() => {
            setCurrentEventIndex(prev => {
                const nextIndex = (prev + 1) % events.length
                setSelectedEventId(events[nextIndex].eventId)
                return nextIndex
            })
        }, playbackSpeed)

        return () => clearInterval(interval)
    }, [isPlaying, events, playbackSpeed])

    // Event selection handler (currently unused but kept for future functionality)
    // const handleEventSelect = (eventId: string) => {
    //     setSelectedEventId(eventId)
    //     const index = events.findIndex(e => e.eventId === eventId)
    //     if (index >= 0) {
    //         setCurrentEventIndex(index)
    //     }
    // }

    const handlePlayPause = () => {
        setIsPlaying(!isPlaying)
    }

    const handlePrevious = () => {
        const prevIndex = currentEventIndex > 0 ? currentEventIndex - 1 : events.length - 1
        setCurrentEventIndex(prevIndex)
        setSelectedEventId(events[prevIndex].eventId)
    }

    const handleNext = () => {
        const nextIndex = (currentEventIndex + 1) % events.length
        setCurrentEventIndex(nextIndex)
        setSelectedEventId(events[nextIndex].eventId)
    }

    const selectedEvent = events.find(e => e.eventId === selectedEventId)

    return (
        <Box sx={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
            <Box sx={{ p: 2 }}>
                <Typography variant="h4" gutterBottom>
                    3D Collision Event Visualization
                </Typography>
                <Typography variant="body1" color="text.secondary" gutterBottom>
                    Interactive 3D visualization of high-energy physics collision events with particle trajectories and detector hits.
                </Typography>
            </Box>

            <Grid container sx={{ flex: 1, minHeight: 0 }}>
                {/* Main Visualization */}
                <Grid item xs={12} md={9} sx={{ height: '100%' }}>
                    <Box sx={{ p: 2, height: '100%' }}>
                        {events.length > 0 ? (
                            <VisualizationCanvas
                                events={selectedEvent ? [selectedEvent] : []}
                                selectedEventId={selectedEventId}
                                height={600}
                            />
                        ) : (
                            <Alert severity="info">
                                Loading collision events...
                            </Alert>
                        )}
                    </Box>
                </Grid>

                {/* Control Panel */}
                <Grid item xs={12} md={3}>
                    <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
                        {/* Playback Controls */}
                        <Card sx={{ mb: 2 }}>
                            <CardContent>
                                <Typography variant="h6" gutterBottom>
                                    Event Playback
                                </Typography>

                                <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
                                    <Button
                                        variant="outlined"
                                        size="small"
                                        onClick={handlePrevious}
                                        startIcon={<SkipPrevious />}
                                    >
                                        Prev
                                    </Button>

                                    <Button
                                        variant="contained"
                                        size="small"
                                        onClick={handlePlayPause}
                                        startIcon={isPlaying ? <Pause /> : <PlayArrow />}
                                    >
                                        {isPlaying ? 'Pause' : 'Play'}
                                    </Button>

                                    <Button
                                        variant="outlined"
                                        size="small"
                                        onClick={handleNext}
                                        startIcon={<SkipNext />}
                                    >
                                        Next
                                    </Button>
                                </Stack>

                                <Typography variant="body2" color="text.secondary">
                                    Event {currentEventIndex + 1} of {events.length}
                                </Typography>
                            </CardContent>
                        </Card>

                        {/* Event Details */}
                        {selectedEvent && (
                            <Card sx={{ mb: 2 }}>
                                <CardContent>
                                    <Typography variant="h6" gutterBottom>
                                        Event Details
                                    </Typography>

                                    <Stack spacing={1}>
                                        <Box>
                                            <Typography variant="body2" color="text.secondary">
                                                Event ID
                                            </Typography>
                                            <Typography variant="body1">
                                                {selectedEvent.eventId}
                                            </Typography>
                                        </Box>

                                        <Box>
                                            <Typography variant="body2" color="text.secondary">
                                                Particle Count
                                            </Typography>
                                            <Typography variant="body1">
                                                {selectedEvent.particleCount}
                                            </Typography>
                                        </Box>

                                        <Box>
                                            <Typography variant="body2" color="text.secondary">
                                                Detector Hits
                                            </Typography>
                                            <Typography variant="body1">
                                                {selectedEvent.detectorHits.length}
                                            </Typography>
                                        </Box>

                                        <Box>
                                            <Typography variant="body2" color="text.secondary">
                                                Particle Tracks
                                            </Typography>
                                            <Typography variant="body1">
                                                {selectedEvent.particleTracks.length}
                                            </Typography>
                                        </Box>
                                    </Stack>
                                </CardContent>
                            </Card>
                        )}

                        {/* Legend */}
                        <Card>
                            <CardContent>
                                <Typography variant="h6" gutterBottom>
                                    Legend
                                </Typography>

                                <Stack spacing={1}>
                                    <Box>
                                        <Typography variant="body2" gutterBottom>
                                            Particle Tracks:
                                        </Typography>
                                        <Stack direction="row" spacing={1} flexWrap="wrap">
                                            <Chip
                                                size="small"
                                                label="Positive"
                                                sx={{ backgroundColor: '#ff0000', color: 'white' }}
                                            />
                                            <Chip
                                                size="small"
                                                label="Negative"
                                                sx={{ backgroundColor: '#0000ff', color: 'white' }}
                                            />
                                            <Chip
                                                size="small"
                                                label="Neutral"
                                                sx={{ backgroundColor: '#00ff00', color: 'black' }}
                                            />
                                        </Stack>
                                    </Box>

                                    <Box>
                                        <Typography variant="body2" gutterBottom>
                                            Detector Hits:
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            Color intensity represents energy level
                                        </Typography>
                                    </Box>

                                    <Box>
                                        <Typography variant="body2" gutterBottom>
                                            Controls:
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            • Mouse drag: Rotate view<br />
                                            • Mouse wheel: Zoom<br />
                                            • Pan mode: Move view
                                        </Typography>
                                    </Box>
                                </Stack>
                            </CardContent>
                        </Card>
                    </Box>
                </Grid>
            </Grid>
        </Box>
    )
}

export default Visualization