import { QueryField, QueryOperator } from '../types/query';

export const QUERY_OPERATORS: QueryOperator[] = [
    { value: '=', label: 'equals', supportedTypes: ['string', 'number', 'date', 'boolean'] },
    { value: '!=', label: 'not equals', supportedTypes: ['string', 'number', 'date', 'boolean'] },
    { value: '>', label: 'greater than', supportedTypes: ['number', 'date'] },
    { value: '>=', label: 'greater than or equal', supportedTypes: ['number', 'date'] },
    { value: '<', label: 'less than', supportedTypes: ['number', 'date'] },
    { value: '<=', label: 'less than or equal', supportedTypes: ['number', 'date'] },
    { value: 'LIKE', label: 'contains', supportedTypes: ['string'] },
    { value: 'NOT LIKE', label: 'does not contain', supportedTypes: ['string'] },
    { value: 'IN', label: 'in list', supportedTypes: ['string', 'number'] },
    { value: 'NOT IN', label: 'not in list', supportedTypes: ['string', 'number'] },
    { value: 'IS NULL', label: 'is empty', supportedTypes: ['string', 'number', 'date', 'geometry'] },
    { value: 'IS NOT NULL', label: 'is not empty', supportedTypes: ['string', 'number', 'date', 'geometry'] },
    { value: 'BETWEEN', label: 'between', supportedTypes: ['number', 'date'] },
    { value: 'ST_DWithin', label: 'within distance', supportedTypes: ['geometry'] },
    { value: 'ST_Contains', label: 'contains geometry', supportedTypes: ['geometry'] },
];

export const COLLISION_EVENT_FIELDS: QueryField[] = [
    { name: 'eventId', label: 'Event ID', type: 'string', table: 'collision_events' },
    { name: 'timestamp', label: 'Timestamp', type: 'date', table: 'collision_events' },
    { name: 'centerOfMassEnergy', label: 'Center of Mass Energy (GeV)', type: 'number', table: 'collision_events' },
    { name: 'runNumber', label: 'Run Number', type: 'number', table: 'collision_events' },
    { name: 'eventNumber', label: 'Event Number', type: 'number', table: 'collision_events' },
    { name: 'collisionVertex', label: 'Collision Vertex', type: 'geometry', table: 'collision_events' },
    { name: 'luminosity', label: 'Luminosity', type: 'number', table: 'collision_events' },
    { name: 'beamEnergy1', label: 'Beam Energy 1 (GeV)', type: 'number', table: 'collision_events' },
    { name: 'beamEnergy2', label: 'Beam Energy 2 (GeV)', type: 'number', table: 'collision_events' },
    { name: 'triggerMask', label: 'Trigger Mask', type: 'number', table: 'collision_events' },
    { name: 'dataQualityFlags', label: 'Data Quality Flags', type: 'number', table: 'collision_events' },
    { name: 'reconstructionVersion', label: 'Reconstruction Version', type: 'string', table: 'collision_events' },
];

export const DETECTOR_HIT_FIELDS: QueryField[] = [
    { name: 'hitId', label: 'Hit ID', type: 'string', table: 'detector_hits' },
    { name: 'detectorId', label: 'Detector ID', type: 'string', table: 'detector_hits' },
    { name: 'energyDeposit', label: 'Energy Deposit (MeV)', type: 'number', table: 'detector_hits' },
    { name: 'hitTime', label: 'Hit Time', type: 'date', table: 'detector_hits' },
    { name: 'position', label: 'Position', type: 'geometry', table: 'detector_hits' },
    { name: 'signalAmplitude', label: 'Signal Amplitude', type: 'number', table: 'detector_hits' },
    { name: 'uncertainty', label: 'Uncertainty', type: 'number', table: 'detector_hits' },
];

export const PARTICLE_TRACK_FIELDS: QueryField[] = [
    { name: 'trackId', label: 'Track ID', type: 'string', table: 'particle_tracks' },
    { name: 'particleType', label: 'Particle Type', type: 'string', table: 'particle_tracks' },
    { name: 'momentum', label: 'Momentum (GeV/c)', type: 'number', table: 'particle_tracks' },
    { name: 'charge', label: 'Charge', type: 'number', table: 'particle_tracks' },
    { name: 'trajectory', label: 'Trajectory', type: 'geometry', table: 'particle_tracks' },
    { name: 'confidenceLevel', label: 'Confidence Level', type: 'number', table: 'particle_tracks' },
    { name: 'chiSquared', label: 'Chi Squared', type: 'number', table: 'particle_tracks' },
    { name: 'degreesOfFreedom', label: 'Degrees of Freedom', type: 'number', table: 'particle_tracks' },
];

export const ALL_QUERY_FIELDS: QueryField[] = [
    ...COLLISION_EVENT_FIELDS,
    ...DETECTOR_HIT_FIELDS,
    ...PARTICLE_TRACK_FIELDS,
];

export const FIELD_GROUPS = {
    'Collision Events': COLLISION_EVENT_FIELDS,
    'Detector Hits': DETECTOR_HIT_FIELDS,
    'Particle Tracks': PARTICLE_TRACK_FIELDS,
};