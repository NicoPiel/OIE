import { useCallback, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useReactTable, getCoreRowModel, getExpandedRowModel, flexRender, type ColumnDef } from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";
import type { components } from "oieapi-types/index.d.ts";
import { Client } from "../../services/Services";
import css from "./DashboardList.module.scss";
import styleIcon from '../../assets/icons/style.png';
import tagIcon from '../../assets/icons/tag_blue.png';
import serverDatabaseIcon from '../../assets/icons/server_database.png';
import serverIcon from '../../assets/icons/server.png';

export const CHANNEL_LIST_QUERY_KEY = 'channelList';

type DashboardStatusDTO = components["schemas"]["DashboardStatus"];
type DashboardChannelInfoDTO = components["schemas"]["DashboardChannelInfo"];
type ChannelGroupDTO = components["schemas"]["ChannelGroup"];

function buildFilterString(prefs: FilterPreferences): string | undefined {
    const text = prefs.textFilter?.trim();
    if (!text) return undefined;
    return `Name:${text}`;
}

async function getDashboardData(prefs: FilterPreferences) {
    const filter = buildFilterString(prefs);

    // Fetch initial statuses + metadata concurrently
    const [initialRes, groupsRes, tagsRes] = await Promise.all([
        Client.GET("/channels/statuses/initial", {
            params: {
                query: filter ? { fetchSize: 100, filter } : { fetchSize: 100 },
            }
        }),
        Client.GET("/channelgroups"),
        Client.GET("/server/channelTags"),
    ]);

    const initial = initialRes.data as unknown as DashboardChannelInfoDTO | undefined;
    if (!initial) throw new Error("Failed to fetch initial dashboard channel info");

    const groups = groupsRes.data;
    if (!groups) throw new Error("Failed to fetch channel groups");

    const tags = tagsRes.data;
    if (!tags) throw new Error("Failed to fetch tags");

    const statuses: DashboardStatusDTO[] = [...(initial.dashboardStatuses ?? [])];
    const remaining = [...(initial.remainingChannelIds ?? [])];

    // Chunk follow-ups to complete the list
    const CHUNK_SIZE = 100;
    while (remaining.length > 0) {
        const chunk = remaining.splice(0, CHUNK_SIZE);
        const { data: moreStatuses } = await Client.GET("/channels/statuses", {
            params: {
                query: {
                    channelId: chunk,
                    includeUndeployed: false,
                    ...(filter ? { filter } : {}),
                },
            },
        });
        if (moreStatuses) {
            statuses.push(...(moreStatuses as DashboardStatusDTO[]));
        }
    }

    return {
        groups,
        tags,
        statuses,
        // Some server builds include deployedChannelCount; fall back to statuses length
        deployedChannelCount: ((initial as unknown as { deployedChannelCount?: number })?.deployedChannelCount) ?? statuses.length,
    };
}

const DASHBOARD_PREFS_KEY = "dashboardListPrefs";

type TagDisplayMode = 'Names' | 'Icons' | 'None';
type StatisticsDisplayMode = 'Current' | 'Lifetime';

interface FilterPreferences {
    textFilter: string;
    useGroups: boolean;
    statsMode: StatisticsDisplayMode;
    tagDisplayMode: TagDisplayMode;
    autoRefresh: boolean;
    autoRefreshMs: number;
}

function getPrefs(): FilterPreferences {
    const saved = localStorage.getItem(DASHBOARD_PREFS_KEY);
    if (saved) {
        const parsed = JSON.parse(saved) as Partial<FilterPreferences>;
        return {
            textFilter: parsed.textFilter ?? '',
            useGroups: parsed.useGroups ?? true,
            statsMode: (parsed.statsMode ?? 'Current') as StatisticsDisplayMode,
            tagDisplayMode: (parsed.tagDisplayMode ?? 'Icons') as TagDisplayMode,
            autoRefresh: parsed.autoRefresh ?? true,
            autoRefreshMs: typeof parsed.autoRefreshMs === 'number' ? parsed.autoRefreshMs : 30000,
        };
    }
    return {
        textFilter: '',
        useGroups: true,
        statsMode: 'Current' as StatisticsDisplayMode,
        tagDisplayMode: 'Icons' as TagDisplayMode,
        autoRefresh: true,
        autoRefreshMs: 30000,
    };
}

export function DashboardList() {
    const [prefs, setPrefsRaw] = useState<FilterPreferences>(getPrefs);
    const setPrefs = useCallback((newPrefs: Partial<FilterPreferences>) => {
        setPrefsRaw(p => {
            const newValue = { ...p, ...newPrefs };
            localStorage.setItem(DASHBOARD_PREFS_KEY, JSON.stringify(newValue));
            return newValue;
        });
    }, []);

    const { data: dashboard } = useQuery({
        queryKey: [CHANNEL_LIST_QUERY_KEY, prefs.textFilter, prefs.statsMode, prefs.autoRefreshMs],
        queryFn: () => getDashboardData(prefs),
        refetchInterval: prefs.autoRefresh ? prefs.autoRefreshMs : false,
    });

    const toggleTagDisplayMode = (button: TagDisplayMode) => {
        if (prefs.tagDisplayMode === button) {
            setPrefs({ tagDisplayMode: 'None' });
        } else {
            setPrefs({ tagDisplayMode: button });
        }
    };

    // Render below shows a loading placeholder while data is being fetched

    // Build hierarchical rows for TanStack Table
    type RowType = {
        key: string;
        channelId?: string;
        statusType?: string | null;
        state?: string | null;
        name?: string | null;
        revDelta?: number | null;
        codeTemplatesChanged?: boolean | null;
        deployedDate?: string | null;
        received?: number;
        filtered?: number;
        queued?: number;
        sent?: number;
        error?: number;
        subRows?: RowType[];
    };

    const toRow = (s: DashboardStatusDTO): RowType => {
        const statsMap = prefs.statsMode === 'Lifetime' ? s.lifetimeStatistics : s.statistics;
        const sm: Record<string, number> = (statsMap ?? {}) as unknown as Record<string, number>;
        return {
            key: String(s.key ?? s.channelId ?? Math.random()),
            channelId: s.channelId ?? undefined,
            statusType: s.statusType ?? null,
            state: (s.state as unknown as string) ?? null,
            name: s.name ?? null,
            revDelta: (s as unknown as { deployedRevisionDelta?: number }).deployedRevisionDelta ?? null,
            codeTemplatesChanged: (s as unknown as { codeTemplatesChanged?: boolean }).codeTemplatesChanged ?? null,
            deployedDate: (s.deployedDate as unknown as string) ?? null,
            received: Number(sm.RECEIVED ?? 0),
            filtered: Number(sm.FILTERED ?? 0),
            queued: Number(s.queued ?? 0),
            sent: Number(sm.SENT ?? 0),
            error: Number(sm.ERROR ?? 0),
            subRows: (s.childStatuses ?? []).map(toRow),
        };
    };

    const rows = useMemo<RowType[]>(() => {
        const source = (dashboard?.statuses as DashboardStatusDTO[]) || [];
        return source.map(toRow);
    }, [dashboard?.statuses, prefs.statsMode]);

    const serverGroups = (dashboard?.groups as ChannelGroupDTO[] | undefined) ?? [];

    const dataRows = useMemo<RowType[]>(() => {
        if (!prefs.useGroups) return rows;

        const groups = serverGroups;
        if (!groups.length) {
            // No groups created on server: show a synthetic "Default" group containing all channels
            const agg: RowType = {
                key: 'group_default',
                name: 'Default',
                received: rows.reduce((a, r) => a + (r.received ?? 0), 0),
                filtered: rows.reduce((a, r) => a + (r.filtered ?? 0), 0),
                queued: rows.reduce((a, r) => a + (r.queued ?? 0), 0),
                sent: rows.reduce((a, r) => a + (r.sent ?? 0), 0),
                error: rows.reduce((a, r) => a + (r.error ?? 0), 0),
                subRows: rows,
            };
            return [agg];
        }

        const byChannel = new Map<string, RowType>();
        rows.forEach(r => { if (r.channelId) byChannel.set(r.channelId, r); });

        const out: RowType[] = [];
        for (const g of groups) {
            const channelIds: string[] = (g.channels ?? []).map(c => c.id!) as string[];
            const subRows = channelIds.map(id => byChannel.get(id)).filter(Boolean) as RowType[];

            const groupRow: RowType = {
                key: `group_${g.id}`,
                name: g.name ?? 'Group',
                received: subRows.reduce((a, r) => a + (r.received ?? 0), 0),
                filtered: subRows.reduce((a, r) => a + (r.filtered ?? 0), 0),
                queued: subRows.reduce((a, r) => a + (r.queued ?? 0), 0),
                sent: subRows.reduce((a, r) => a + (r.sent ?? 0), 0),
                error: subRows.reduce((a, r) => a + (r.error ?? 0), 0),
                subRows,
            };
            out.push(groupRow);
        }

        return out;
    }, [rows, prefs.useGroups, serverGroups]);

    const columns = useMemo<ColumnDef<RowType>[]>(() => [
        {
            id: 'expander',
            header: '',
            cell: ({ row }) => {
                if (!row.getCanExpand()) return null;
                return (
                    <button
                        type="button"
                        className="btn btn-sm btn-link"
                        onClick={row.getToggleExpandedHandler()}
                        aria-label={row.getIsExpanded() ? "Collapse" : "Expand"}>
                        {row.getIsExpanded() ? '▼' : '▶'}
                    </button>
                );
            },
            size: 32,
        },
        { header: 'Status', accessorKey: 'state' },
        { header: 'Name', accessorKey: 'name' },
        { header: 'Rev Δ', accessorKey: 'revDelta' },
        {
            header: 'Last Deployed',
            accessorKey: 'deployedDate',
            cell: info => info.getValue() ? new Date(info.getValue() as string).toLocaleString() : '',
        },
        { header: 'Received', accessorKey: 'received' },
        { header: 'Filtered', accessorKey: 'filtered' },
        { header: 'Queued', accessorKey: 'queued' },
        { header: 'Sent', accessorKey: 'sent' },
        { header: 'Errored', accessorKey: 'error' },
    ], []);

    const table = useReactTable({
        data: dataRows,
        columns,
        getCoreRowModel: getCoreRowModel(),
        getExpandedRowModel: getExpandedRowModel(),
        getSubRows: (row) => row.subRows,
    });

    const parentRef = useRef<HTMLDivElement>(null);
    const rowVirtualizer = useVirtualizer({
        count: table.getRowModel().rows.length,
        getScrollElement: () => parentRef.current,
        estimateSize: () => 28,
        overscan: 10,
    });
    const virtualRows = rowVirtualizer.getVirtualItems();

    const groupsCount = prefs.useGroups ? serverGroups.length || dataRows.length : 0;
    const deployedCount = dashboard?.deployedChannelCount ?? rows.length;

    return <div className={`card p-2 ${css.dashboardListCard}`}>
        <div className={css.dashboardList} ref={parentRef}>
            {!dashboard ? (
                <span>Loading...</span>
            ) : (
                <table className="table table-sm table-striped">
                    <thead>
                        {table.getHeaderGroups().map(headerGroup => (
                            <tr key={headerGroup.id}>
                                {headerGroup.headers.map(header => (
                                    <th key={header.id} style={{ width: header.getSize() ? `${header.getSize()}px` : undefined }}>
                                        {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                                    </th>
                                ))}
                            </tr>
                        ))}
                    </thead>
                    <tbody>
                        {virtualRows.length === 0 ? (
                            <>
                                {table.getRowModel().rows.map(row => (
                                    <tr key={row.id}>
                                        {row.getVisibleCells().map(cell => (
                                            <td key={cell.id} style={cell.column.id === 'expander' ? { paddingLeft: `${row.depth * 16}px` } : undefined}>
                                                {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </>
                        ) : (
                            <>
                                <tr>
                                    <td colSpan={table.getVisibleFlatColumns().length} style={{ height: virtualRows[0].start }} />
                                </tr>
                                {virtualRows.map(virtualRow => {
                                    const row = table.getRowModel().rows[virtualRow.index];
                                    return (
                                        <tr key={row.id} style={{ height: virtualRow.size }}>
                                            {row.getVisibleCells().map(cell => (
                                                <td key={cell.id} style={cell.column.id === 'expander' ? { paddingLeft: `${row.depth * 16}px` } : undefined}>
                                                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                                </td>
                                            ))}
                                        </tr>
                                    );
                                })}
                                <tr>
                                    <td colSpan={table.getVisibleFlatColumns().length} style={{ height: rowVirtualizer.getTotalSize() - virtualRows[virtualRows.length - 1].end }} />
                                </tr>
                            </>
                        )}
                    </tbody>
                </table>
            )}
        </div>
        <div className={css.toolbar}>
            <form className="d-flex flex-row align-items-center" onSubmit={e => e.preventDefault()}>
                <label htmlFor="dashboard-search-box" className="text-nowrap me-2">
                    Filter:
                </label>
                <input id="dashboard-search-box" className="form-control d-inline-block" type="text" value={prefs.textFilter}
                    onChange={e => setPrefs({ textFilter: e.target.value })} />
                <button type="button" className="btn btn-outline" onClick={() => setPrefs({ textFilter: '' })} title="Clear Filter" aria-label="Clear Filter">
                    ✕
                </button>
            </form>

            <div>
                {groupsCount} Groups, {deployedCount} Deployed Channels
            </div>

            <div style={{ marginLeft: 'auto' }}>
                <input type="radio" id="dashboard-stats-mode" value="Current" name="dashboard-stats-mode"
                    checked={prefs.statsMode === 'Current'} onChange={() => setPrefs({ statsMode: 'Current' })} />
                <label htmlFor="dashboard-stats-mode" className="ms-1 me-2">Current Statistics</label>

                <input type="radio" id="dashboard-stats-mode-lifetime" value="Lifetime" name="dashboard-stats-mode"
                    checked={prefs.statsMode === 'Lifetime'} onChange={() => setPrefs({ statsMode: 'Lifetime' })} />
                <label htmlFor="dashboard-stats-mode-lifetime" className="ms-1 me-2">Lifetime Statistics</label>

                <div className="form-check form-switch ms-3">
                    <input className="form-check-input" type="checkbox" id="auto-refresh-toggle"
                        checked={prefs.autoRefresh} onChange={() => setPrefs({ autoRefresh: !prefs.autoRefresh })} />
                    <label className="form-check-label ms-1" htmlFor="auto-refresh-toggle">Auto Refresh</label>
                </div>
                <label htmlFor="auto-refresh-interval" className="ms-2 me-2">Interval (s):</label>
                <input
                    id="auto-refresh-interval"
                    className="form-control d-inline-block"
                    type="number"
                    min={5}
                    step={5}
                    style={{ width: '6rem' }}
                    value={Math.max(5, Math.round(prefs.autoRefreshMs / 1000))}
                    onChange={(e) => {
                        const seconds = Math.max(5, parseInt(e.target.value || '30', 10));
                        setPrefs({ autoRefreshMs: seconds * 1000 });
                    }}
                />
            </div>

            <div className={css.separator} />

            <button className={`btn btn-outline ${prefs.tagDisplayMode === 'Names' ? 'active' : ''}`} onClick={() => toggleTagDisplayMode('Names')}
                title="Toggle Tag Names" aria-label="Toggle Tag Names">
                <img src={styleIcon} aria-hidden="true" />
            </button>
            <button className={`btn btn-outline ${prefs.tagDisplayMode === 'Icons' ? 'active' : ''}`} onClick={() => toggleTagDisplayMode('Icons')}
                title="Toggle Tag Icons" aria-label="Toggle Tag Icons">
                <img src={tagIcon} aria-hidden="true" />
            </button>

            <div className={css.separator} />

            <button className={`btn btn-outline ${prefs.useGroups ? 'active' : ''}`} onClick={() => setPrefs({ useGroups: true })}
                title="Show Groups" aria-label="Show Groups">
                <img src={serverDatabaseIcon} aria-hidden="true" />
            </button>
            <button className={`btn btn-outline ${!prefs.useGroups ? 'active' : ''}`} onClick={() => setPrefs({ useGroups: false })}
                title="Hide Groups" aria-label="Hide Groups">
                <img src={serverIcon} aria-hidden="true" />
            </button>
        </div>
    </div>;
}
