import { useCallback, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useReactTable, getCoreRowModel, getExpandedRowModel, flexRender, type ColumnDef } from "@tanstack/react-table";
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

function buildFilterString(prefs: FilterPreferences): string | undefined {
    const text = prefs.textFilter?.trim();
    if (!text) return undefined;
    return `Name:${text}`;
}

async function getDashboardData(prefs: FilterPreferences) {
    // Fetch initial statuses + metadata concurrently
    const [initialRes, groupsRes, tagsRes] = await Promise.all([
        Client.GET("/channels/statuses/initial", {
            params: {
                query: {
                    fetchSize: 100,
                    filter: buildFilterString(prefs)
                }
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
                    filter: buildFilterString(prefs),
                }
            }
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
}

function getPrefs(): FilterPreferences {
    const saved = localStorage.getItem(DASHBOARD_PREFS_KEY);
    if (saved) {
        return JSON.parse(saved) as FilterPreferences;
    }
    return {
        textFilter: '',
        useGroups: true,
        statsMode: 'Current' as StatisticsDisplayMode,
        tagDisplayMode: 'Icons' as TagDisplayMode,
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
        queryKey: [CHANNEL_LIST_QUERY_KEY, prefs.textFilter, prefs.statsMode],
        queryFn: () => getDashboardData(prefs),
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
        data: rows,
        columns,
        getCoreRowModel: getCoreRowModel(),
        getExpandedRowModel: getExpandedRowModel(),
        getSubRows: (row) => row.subRows,
    });

    const groupsCount = (dashboard && Array.isArray(dashboard.groups)) ? dashboard.groups.length : 0;
    const deployedCount = dashboard?.deployedChannelCount ?? rows.length;

    return <div className={`card p-2 ${css.dashboardListCard}`}>
        <div className={css.dashboardList}>
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
                        {table.getRowModel().rows.map(row => (
                            <tr key={row.id}>
                                {row.getVisibleCells().map(cell => (
                                    <td key={cell.id} style={cell.column.id === 'expander' ? { paddingLeft: `${row.depth * 16}px` } : undefined}>
                                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                    </td>
                                ))}
                            </tr>
                        ))}
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
