import Link from "next/link";
import { formatDateTime, scoreHistory } from "@/lib/report";
import type { Scan } from "@/lib/types";

const WIDTH = 320;
const HEIGHT = 72;
const PAD = 6;

export function ScoreHistory({ scans }: { scans: Scan[] }) {
  if (scans.length === 0) return null;
  const points = scoreHistory(scans);
  const step = points.length > 1 ? (WIDTH - 2 * PAD) / (points.length - 1) : 0;
  const coords = points.map((point, index) => ({
    x: PAD + index * step,
    y: PAD + (1 - point.score / 100) * (HEIGHT - 2 * PAD),
  }));

  return (
    <section className="history" aria-labelledby="history-title">
      <h2 id="history-title">Historique</h2>
      {points.length > 1 && (
        <svg
          className="history-chart"
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          role="img"
          aria-label={`Évolution du score sur ${points.length} scans, de ${points[0].score} à ${points[points.length - 1].score}`}
        >
          <polyline points={coords.map((c) => `${c.x},${c.y}`).join(" ")} />
          {coords.map((c, index) => (
            <circle key={points[index].id} cx={c.x} cy={c.y} r="3" />
          ))}
        </svg>
      )}
      <ul className="history-list">
        {scans.map((scan) => (
          <li key={scan.id}>
            <Link href={`/scans/${scan.id}`}>{formatDateTime(scan.createdAt)}</Link>
            <span className="history-result">
              {scan.status === "SUCCEEDED" && `${scan.grade} (${scan.score})`}
              {scan.status === "FAILED" && "Échec"}
              {scan.status === "QUEUED" && "En cours"}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
