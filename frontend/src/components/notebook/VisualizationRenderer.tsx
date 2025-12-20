import React, { useRef, useEffect } from 'react';
import * as d3 from 'd3';
import { Box, Paper, Typography } from '@mui/material';

interface VisualizationRendererProps {
    data: any;
    type: 'trajectory' | 'histogram' | 'scatter' | 'd3-custom';
    width?: number;
    height?: number;
}

const VisualizationRenderer: React.FC<VisualizationRendererProps> = ({
    data,
    type,
    width = 600,
    height = 400,
}) => {
    const svgRef = useRef<SVGSVGElement>(null);

    useEffect(() => {
        if (!svgRef.current || !data) return;

        const svg = d3.select(svgRef.current);
        svg.selectAll('*').remove(); // Clear previous content

        const margin = { top: 20, right: 30, bottom: 40, left: 50 };
        const innerWidth = width - margin.left - margin.right;
        const innerHeight = height - margin.top - margin.bottom;

        const g = svg
            .append('g')
            .attr('transform', `translate(${margin.left},${margin.top})`);

        switch (type) {
            case 'trajectory':
                renderTrajectoryPlot(g, data, innerWidth, innerHeight);
                break;
            case 'histogram':
                renderHistogram(g, data, innerWidth, innerHeight);
                break;
            case 'scatter':
                renderScatterPlot(g, data, innerWidth, innerHeight);
                break;
            case 'd3-custom':
                renderCustomD3(g, data, innerWidth, innerHeight);
                break;
        }
    }, [data, type, width, height]);

    const renderTrajectoryPlot = (g: any, data: any, width: number, height: number) => {
        // Mock particle trajectory data
        const trajectories = data.trajectories || [
            { id: 1, points: generateTrajectory(0, 0, Math.PI / 4, 100), charge: 1 },
            { id: 2, points: generateTrajectory(0, 0, -Math.PI / 6, 80), charge: -1 },
            { id: 3, points: generateTrajectory(0, 0, Math.PI / 3, 120), charge: 1 },
        ];

        const allPoints = trajectories.flatMap((t: any) => t.points);
        const xExtent = d3.extent(allPoints, (d: any) => d.x) || [0, 100];
        const yExtent = d3.extent(allPoints, (d: any) => d.y) || [0, 100];

        const xScale = d3.scaleLinear()
            .domain(xExtent as unknown as [number, number])
            .range([0, width]);

        const yScale = d3.scaleLinear()
            .domain(yExtent as unknown as [number, number])
            .range([height, 0]);

        const colorScale = d3.scaleOrdinal()
            .domain(['-1', '1'])
            .range(['#ff6b6b', '#4ecdc4']);

        // Draw axes
        g.append('g')
            .attr('transform', `translate(0,${height})`)
            .call(d3.axisBottom(xScale))
            .append('text')
            .attr('x', width / 2)
            .attr('y', 35)
            .attr('fill', 'black')
            .style('text-anchor', 'middle')
            .text('X Position (cm)');

        g.append('g')
            .call(d3.axisLeft(yScale))
            .append('text')
            .attr('transform', 'rotate(-90)')
            .attr('y', -35)
            .attr('x', -height / 2)
            .attr('fill', 'black')
            .style('text-anchor', 'middle')
            .text('Y Position (cm)');

        // Draw trajectories
        const line = d3.line<{ x: number, y: number }>()
            .x(d => xScale(d.x))
            .y(d => yScale(d.y))
            .curve(d3.curveBasis);

        trajectories.forEach((trajectory: any) => {
            g.append('path')
                .datum(trajectory.points)
                .attr('fill', 'none')
                .attr('stroke', colorScale(trajectory.charge.toString()))
                .attr('stroke-width', 2)
                .attr('d', line);

            // Add particle markers
            g.selectAll(`.particle-${trajectory.id}`)
                .data(trajectory.points.filter((_: any, i: number) => i % 5 === 0))
                .enter()
                .append('circle')
                .attr('class', `particle-${trajectory.id}`)
                .attr('cx', (d: any) => xScale(d.x))
                .attr('cy', (d: any) => yScale(d.y))
                .attr('r', 2)
                .attr('fill', colorScale(trajectory.charge.toString()));
        });

        // Add title
        g.append('text')
            .attr('x', width / 2)
            .attr('y', -5)
            .attr('text-anchor', 'middle')
            .style('font-size', '14px')
            .style('font-weight', 'bold')
            .text('Particle Trajectories');
    };

    const renderHistogram = (g: any, data: any, width: number, height: number) => {
        const values = data.values || d3.range(1000).map(() => d3.randomNormal(50, 15)());

        const valuesExtent = d3.extent(values) || [0, 100];
        const xScale = d3.scaleLinear()
            .domain(valuesExtent as unknown as [number, number])
            .range([0, width]);

        const bins = d3.histogram()
            .domain(xScale.domain() as [number, number])
            .thresholds(xScale.ticks(30))(values);

        const yScale = d3.scaleLinear()
            .domain([0, d3.max(bins, d => d.length) as number])
            .range([height, 0]);

        // Draw axes
        g.append('g')
            .attr('transform', `translate(0,${height})`)
            .call(d3.axisBottom(xScale))
            .append('text')
            .attr('x', width / 2)
            .attr('y', 35)
            .attr('fill', 'black')
            .style('text-anchor', 'middle')
            .text('Energy (GeV)');

        g.append('g')
            .call(d3.axisLeft(yScale))
            .append('text')
            .attr('transform', 'rotate(-90)')
            .attr('y', -35)
            .attr('x', -height / 2)
            .attr('fill', 'black')
            .style('text-anchor', 'middle')
            .text('Frequency');

        // Draw bars
        g.selectAll('.bar')
            .data(bins)
            .enter()
            .append('rect')
            .attr('class', 'bar')
            .attr('x', (d: any) => xScale(d.x0!))
            .attr('y', (d: any) => yScale(d.length))
            .attr('width', (d: any) => Math.max(0, xScale(d.x1!) - xScale(d.x0!) - 1))
            .attr('height', (d: any) => height - yScale(d.length))
            .attr('fill', '#4ecdc4')
            .attr('opacity', 0.7);

        // Add title
        g.append('text')
            .attr('x', width / 2)
            .attr('y', -5)
            .attr('text-anchor', 'middle')
            .style('font-size', '14px')
            .style('font-weight', 'bold')
            .text('Energy Distribution');
    };

    const renderScatterPlot = (g: any, data: any, width: number, height: number) => {
        const points = data.points || d3.range(200).map(() => ({
            x: Math.random() * 100,
            y: Math.random() * 100,
            energy: Math.random() * 1000,
        }));

        const xExtent = d3.extent(points, (d: any) => d.x) || [0, 100];
        const yExtent = d3.extent(points, (d: any) => d.y) || [0, 100];
        const energyExtent = d3.extent(points, (d: any) => d.energy) || [0, 1000];

        const xScale = d3.scaleLinear()
            .domain(xExtent as unknown as [number, number])
            .range([0, width]);

        const yScale = d3.scaleLinear()
            .domain(yExtent as unknown as [number, number])
            .range([height, 0]);

        const colorScale = d3.scaleSequential(d3.interpolateViridis)
            .domain(energyExtent as unknown as [number, number]);

        // Draw axes
        g.append('g')
            .attr('transform', `translate(0,${height})`)
            .call(d3.axisBottom(xScale));

        g.append('g')
            .call(d3.axisLeft(yScale));

        // Draw points
        g.selectAll('.point')
            .data(points)
            .enter()
            .append('circle')
            .attr('class', 'point')
            .attr('cx', (d: any) => xScale(d.x))
            .attr('cy', (d: any) => yScale(d.y))
            .attr('r', 3)
            .attr('fill', (d: any) => colorScale(d.energy))
            .attr('opacity', 0.7);
    };

    const renderCustomD3 = (g: any, data: any, width: number, height: number) => {
        // Custom physics visualization
        const nodes = data.nodes || d3.range(50).map(i => ({
            id: i,
            x: Math.random() * width,
            y: Math.random() * height,
            charge: Math.random() > 0.5 ? 1 : -1,
        }));

        const colorScale = d3.scaleOrdinal()
            .domain(['-1', '1'])
            .range(['#ff6b6b', '#4ecdc4']);

        // Create force simulation
        const simulation = d3.forceSimulation(nodes)
            .force('charge', d3.forceManyBody().strength(-30))
            .force('center', d3.forceCenter(width / 2, height / 2))
            .force('collision', d3.forceCollide().radius(10));

        const node = g.selectAll('.node')
            .data(nodes)
            .enter()
            .append('circle')
            .attr('class', 'node')
            .attr('r', 5)
            .attr('fill', (d: any) => colorScale(d.charge.toString()))
            .call(d3.drag()
                .on('start', dragstarted)
                .on('drag', dragged)
                .on('end', dragended));

        simulation.on('tick', () => {
            node
                .attr('cx', (d: any) => d.x)
                .attr('cy', (d: any) => d.y);
        });

        function dragstarted(event: any, d: any) {
            if (!event.active) simulation.alphaTarget(0.3).restart();
            d.fx = d.x;
            d.fy = d.y;
        }

        function dragged(event: any, d: any) {
            d.fx = event.x;
            d.fy = event.y;
        }

        function dragended(event: any, d: any) {
            if (!event.active) simulation.alphaTarget(0);
            d.fx = null;
            d.fy = null;
        }
    };

    const generateTrajectory = (x0: number, y0: number, angle: number, steps: number) => {
        const points = [];
        for (let i = 0; i < steps; i++) {
            const t = i / steps;
            points.push({
                x: x0 + t * 100 * Math.cos(angle) + 0.1 * t * t * 50,
                y: y0 + t * 100 * Math.sin(angle) - 0.5 * 9.8 * t * t * 10,
            });
        }
        return points;
    };

    return (
        <Paper sx={{ p: 2, mb: 2 }}>
            <Typography variant="h6" gutterBottom>
                Physics Visualization
            </Typography>
            <Box sx={{ display: 'flex', justifyContent: 'center' }}>
                <svg
                    ref={svgRef}
                    width={width}
                    height={height}
                    style={{ border: '1px solid #ddd' }}
                />
            </Box>
        </Paper>
    );
};

export default VisualizationRenderer;