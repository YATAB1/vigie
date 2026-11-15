import { gradeTone } from "@/lib/report";

const SIZE = 220;
const STROKE = 14;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/** The grade inside a ring whose filled arc is the score out of 100. */
export function GradeLens({ grade, score }: { grade: string; score: number }) {
  const filled = (Math.max(0, Math.min(100, score)) / 100) * CIRCUMFERENCE;
  return (
    <figure className="lens" data-tone={gradeTone(grade)}>
      <svg
        viewBox={`0 0 ${SIZE} ${SIZE}`}
        role="img"
        aria-label={`Note ${grade}, ${score} sur 100`}
      >
        <circle className="lens-track" cx={SIZE / 2} cy={SIZE / 2} r={RADIUS} strokeWidth={STROKE} />
        <circle
          className="lens-arc"
          cx={SIZE / 2}
          cy={SIZE / 2}
          r={RADIUS}
          strokeWidth={STROKE}
          strokeDasharray={`${filled} ${CIRCUMFERENCE}`}
          transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
        />
        <text className="lens-grade" x="50%" y="50%" dominantBaseline="central" textAnchor="middle">
          {grade}
        </text>
      </svg>
      <figcaption>
        <strong>{score}</strong> sur 100
      </figcaption>
    </figure>
  );
}
