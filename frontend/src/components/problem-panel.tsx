import ReactMarkdown from 'react-markdown'
import { Badge } from '@/components/ui/badge'
import { Clock, Cpu } from 'lucide-react'
import type { ProblemDetail } from '@/lib/types'

export function ProblemPanel({ problem }: { problem: ProblemDetail }) {
  return (
    <div className="flex flex-col h-full glass border border-border rounded-lg overflow-hidden">
      <div className="px-6 h-[64px] border-b border-border flex items-center shrink-0">
        <div className="flex items-center gap-3 w-full">
          <div className="w-8 h-8 rounded-md bg-secondary flex items-center justify-center text-sm font-bold text-accent shrink-0">
            {problem.label}
          </div>
          <h1 className="text-xl font-bold text-foreground truncate">{problem.title}</h1>
          <div className="flex items-center gap-2 ml-auto shrink-0">
            <Badge variant="outline" className="gap-1 text-muted-foreground">
              <Clock size={12} /> {problem.timeLimitMs} ms
            </Badge>
            <Badge variant="outline" className="gap-1 text-muted-foreground">
              <Cpu size={12} /> {problem.memoryLimitMb} MB
            </Badge>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-8 space-y-8">
        <div className="prose prose-invert max-w-none text-sm leading-[1.7] text-foreground prose-headings:text-white prose-code:text-accent prose-pre:bg-secondary prose-pre:border prose-pre:border-border">
          <ReactMarkdown>{problem.statementMd}</ReactMarkdown>
        </div>

        {problem.samples.length > 0 && (
          <div className="space-y-5">
            <h3 className="text-base font-bold text-foreground">Examples</h3>
            {problem.samples.map((s) => (
              <div key={s.ordinal} className="grid md:grid-cols-2 gap-3">
                <div>
                  <div className="text-[11px] uppercase font-bold text-muted-foreground mb-1.5 tracking-wider">
                    Input
                  </div>
                  <pre className="bg-secondary border border-border rounded-md p-3 text-sm font-mono text-foreground whitespace-pre-wrap break-words">
                    {s.input}
                  </pre>
                </div>
                <div>
                  <div className="text-[11px] uppercase font-bold text-muted-foreground mb-1.5 tracking-wider">
                    Expected output
                  </div>
                  <pre className="bg-secondary border border-border rounded-md p-3 text-sm font-mono text-foreground whitespace-pre-wrap break-words">
                    {s.expectedOutput}
                  </pre>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
