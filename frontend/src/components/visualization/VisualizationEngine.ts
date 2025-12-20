import * as THREE from 'three'

export interface CollisionEvent {
    eventId: string
    particleCount: number
    detectorHits: DetectorHit[]
    particleTracks: ParticleTrack[]
}

export interface DetectorHit {
    x: number
    y: number
    z: number
    energy: number
}

export interface ParticleTrack {
    points: Point3D[]
    momentum: number
    charge: number
}

export interface Point3D {
    x: number
    y: number
    z: number
}

export interface RenderResult {
    renderTime: number
    hasDetectorGeometry: boolean
    hasParticleTracks: boolean
    hasDetectorHits: boolean
    success: boolean
}

export interface InteractionEvent {
    type: 'rotation' | 'zoom' | 'pan'
    deltaX?: number
    deltaY?: number
    zoomFactor?: number
}

export interface InteractionResult {
    responseTime: number
    success: boolean
    maintainsPerformance: boolean
}

export interface DataUpdate {
    type: 'new_event' | 'event_modified' | 'event_deleted'
    eventId: string
    eventData?: CollisionEvent
    timestamp: number
}

export interface UpdateResult {
    updateTime: number
    displayRefreshed: boolean
    automaticUpdate: boolean
    success: boolean
}

export class VisualizationEngine {
    private renderer!: THREE.WebGLRenderer
    private scene!: THREE.Scene
    private camera!: THREE.PerspectiveCamera
    private loadedEvents: CollisionEvent[] = []
    private eventGroups: Map<string, THREE.Group> = new Map()
    private detectorGeometry: THREE.Mesh | null = null
    private animationId: number | null = null

    constructor(container: HTMLElement) {
        this.initializeRenderer(container)
        this.initializeScene()
        this.initializeCamera()
        this.setupDetectorGeometry()
        this.startRenderLoop()
    }

    private initializeRenderer(container: HTMLElement): void {
        this.renderer = new THREE.WebGLRenderer({
            antialias: true,
            alpha: true
        })
        this.renderer.setSize(container.clientWidth, container.clientHeight)
        this.renderer.setPixelRatio(window.devicePixelRatio)
        this.renderer.setClearColor(0x000011, 1)
        this.renderer.shadowMap.enabled = true
        this.renderer.shadowMap.type = THREE.PCFSoftShadowMap

        container.appendChild(this.renderer.domElement)
    }

    private initializeScene(): void {
        this.scene = new THREE.Scene()

        // Add ambient light
        const ambientLight = new THREE.AmbientLight(0x404040, 0.4)
        this.scene.add(ambientLight)

        // Add directional light
        const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8)
        directionalLight.position.set(50, 50, 50)
        directionalLight.castShadow = true
        this.scene.add(directionalLight)
    }

    private initializeCamera(): void {
        this.camera = new THREE.PerspectiveCamera(
            75,
            window.innerWidth / window.innerHeight,
            0.1,
            1000
        )
        this.camera.position.set(100, 100, 100)
        this.camera.lookAt(0, 0, 0)
    }

    private setupDetectorGeometry(): void {
        // Create detector geometry (wireframe box representing the detector)
        const detectorGeometry = new THREE.BoxGeometry(100, 100, 100)
        const detectorMaterial = new THREE.MeshBasicMaterial({
            color: 0x888888,
            wireframe: true,
            transparent: true,
            opacity: 0.3
        })
        this.detectorGeometry = new THREE.Mesh(detectorGeometry, detectorMaterial)
        this.scene.add(this.detectorGeometry)
    }

    private startRenderLoop(): void {
        const animate = () => {
            this.animationId = requestAnimationFrame(animate)
            this.renderer.render(this.scene, this.camera)
        }
        animate()
    }

    public renderCollisionEvent(event: CollisionEvent): RenderResult {
        const startTime = performance.now()

        try {
            // Remove existing event if it exists
            if (this.eventGroups.has(event.eventId)) {
                const existingGroup = this.eventGroups.get(event.eventId)!
                this.scene.remove(existingGroup)
                this.eventGroups.delete(event.eventId)
            }

            // Create new event group
            const eventGroup = new THREE.Group()
            eventGroup.name = `event-${event.eventId}`

            // Render particle trajectories
            this.renderParticleTracks(event.particleTracks, eventGroup)

            // Render detector hits
            this.renderDetectorHits(event.detectorHits, eventGroup)

            // Add to scene
            this.scene.add(eventGroup)
            this.eventGroups.set(event.eventId, eventGroup)

            const endTime = performance.now()
            const renderTime = endTime - startTime

            return {
                renderTime,
                hasDetectorGeometry: this.detectorGeometry !== null,
                hasParticleTracks: event.particleTracks.length > 0,
                hasDetectorHits: event.detectorHits.length > 0,
                success: true
            }
        } catch (error) {
            console.error('Error rendering collision event:', error)
            const endTime = performance.now()
            return {
                renderTime: endTime - startTime,
                hasDetectorGeometry: false,
                hasParticleTracks: false,
                hasDetectorHits: false,
                success: false
            }
        }
    }

    private renderParticleTracks(tracks: ParticleTrack[], parent: THREE.Group): void {
        tracks.forEach((track, index) => {
            if (track.points.length < 2) return

            const points = track.points.map(p => new THREE.Vector3(p.x, p.y, p.z))
            const geometry = new THREE.BufferGeometry().setFromPoints(points)

            // Color based on charge: red for positive, blue for negative, green for neutral
            let color = 0x00ff00 // neutral
            if (track.charge > 0) color = 0xff0000 // positive
            else if (track.charge < 0) color = 0x0000ff // negative

            const material = new THREE.LineBasicMaterial({
                color,
                linewidth: 2
            })

            const line = new THREE.Line(geometry, material)
            line.name = `track-${index}`
            parent.add(line)
        })
    }

    private renderDetectorHits(hits: DetectorHit[], parent: THREE.Group): void {
        hits.forEach((hit, index) => {
            const hitGeometry = new THREE.SphereGeometry(0.8, 8, 6)

            // Color based on energy (HSL: energy maps to hue)
            const hue = Math.min(hit.energy / 100, 1) * 0.7 // 0 to 0.7 (red to blue)
            const color = new THREE.Color().setHSL(hue, 1, 0.5)

            const hitMaterial = new THREE.MeshBasicMaterial({ color })
            const hitMesh = new THREE.Mesh(hitGeometry, hitMaterial)

            hitMesh.position.set(hit.x, hit.y, hit.z)
            hitMesh.name = `hit-${index}`
            parent.add(hitMesh)
        })
    }

    public loadMultipleEvents(events: CollisionEvent[]): void {
        // Clear existing events
        this.clearAllEvents()

        // Load new events
        this.loadedEvents = [...events]
        events.forEach(event => {
            this.renderCollisionEvent(event)
        })
    }

    public handleInteraction(interaction: InteractionEvent): InteractionResult {
        const startTime = performance.now()

        try {
            switch (interaction.type) {
                case 'rotation':
                    if (interaction.deltaX !== undefined && interaction.deltaY !== undefined) {
                        // Rotate camera around the scene
                        const spherical = new THREE.Spherical()
                        spherical.setFromVector3(this.camera.position)
                        spherical.theta += interaction.deltaX * 0.01
                        spherical.phi += interaction.deltaY * 0.01
                        spherical.phi = Math.max(0.1, Math.min(Math.PI - 0.1, spherical.phi))
                        this.camera.position.setFromSpherical(spherical)
                        this.camera.lookAt(0, 0, 0)
                    }
                    break

                case 'zoom':
                    if (interaction.zoomFactor !== undefined) {
                        const direction = new THREE.Vector3()
                        this.camera.getWorldDirection(direction)
                        const distance = this.camera.position.length()
                        const newDistance = Math.max(10, Math.min(500, distance / interaction.zoomFactor))
                        this.camera.position.normalize().multiplyScalar(newDistance)
                    }
                    break

                case 'pan':
                    if (interaction.deltaX !== undefined && interaction.deltaY !== undefined) {
                        const panSpeed = 0.1
                        const right = new THREE.Vector3()
                        const up = new THREE.Vector3()
                        this.camera.getWorldDirection(right)
                        right.cross(this.camera.up).normalize()
                        up.copy(this.camera.up)

                        const panVector = right.multiplyScalar(-interaction.deltaX * panSpeed)
                            .add(up.multiplyScalar(interaction.deltaY * panSpeed))

                        this.camera.position.add(panVector)
                    }
                    break
            }

            const endTime = performance.now()
            const responseTime = endTime - startTime

            return {
                responseTime,
                success: true,
                maintainsPerformance: responseTime <= 33 // 30fps minimum
            }
        } catch (error) {
            console.error('Error handling interaction:', error)
            const endTime = performance.now()
            return {
                responseTime: endTime - startTime,
                success: false,
                maintainsPerformance: false
            }
        }
    }

    public handleDataUpdate(update: DataUpdate): UpdateResult {
        const startTime = performance.now()

        try {
            let displayRefreshed = false
            let automaticUpdate = false

            switch (update.type) {
                case 'new_event':
                    if (update.eventData) {
                        this.loadedEvents.push(update.eventData)
                        this.renderCollisionEvent(update.eventData)
                        displayRefreshed = true
                        automaticUpdate = true
                    }
                    break

                case 'event_modified':
                    const eventIndex = this.loadedEvents.findIndex(e => e.eventId === update.eventId)
                    if (eventIndex >= 0 && update.eventData) {
                        this.loadedEvents[eventIndex] = update.eventData
                        this.renderCollisionEvent(update.eventData)
                        displayRefreshed = true
                        automaticUpdate = true
                    }
                    break

                case 'event_deleted':
                    const deleteIndex = this.loadedEvents.findIndex(e => e.eventId === update.eventId)
                    if (deleteIndex >= 0) {
                        this.loadedEvents.splice(deleteIndex, 1)
                        const eventGroup = this.eventGroups.get(update.eventId)
                        if (eventGroup) {
                            this.scene.remove(eventGroup)
                            this.eventGroups.delete(update.eventId)
                        }
                        displayRefreshed = true
                        automaticUpdate = true
                    }
                    break
            }

            const endTime = performance.now()
            const updateTime = endTime - startTime

            return {
                updateTime,
                displayRefreshed,
                automaticUpdate,
                success: true
            }
        } catch (error) {
            console.error('Error handling data update:', error)
            const endTime = performance.now()
            return {
                updateTime: endTime - startTime,
                displayRefreshed: false,
                automaticUpdate: false,
                success: false
            }
        }
    }

    public clearAllEvents(): void {
        this.eventGroups.forEach((group) => {
            this.scene.remove(group)
        })
        this.eventGroups.clear()
        this.loadedEvents = []
    }

    public resize(width: number, height: number): void {
        this.camera.aspect = width / height
        this.camera.updateProjectionMatrix()
        this.renderer.setSize(width, height)
    }

    public dispose(): void {
        if (this.animationId) {
            cancelAnimationFrame(this.animationId)
        }
        this.clearAllEvents()
        this.renderer.dispose()
    }
}