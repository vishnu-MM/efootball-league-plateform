import { AlertTriangle, Check, MessageSquare } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '../api/client'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { TextField } from '../components/ui/Field'
import { EmptyState, ErrorState, ListSkeleton } from '../components/ui/States'
import { IconTile, Tile } from '../components/ui/Tile'
import { DISCORD_TOPICS, useDiscordActions, useDiscordSettings, type DiscordSettings, type DiscordTopic } from '../features/discord/useDiscord'
import { formatWhen } from '../features/activity/useLog'

/** Owner: connect a Discord channel through a webhook and pick which league updates it gets. */
export function DiscordSettingsPage() {
  const { slug = '' } = useParams()
  const { data, isPending, isError, error, refetch } = useDiscordSettings(slug)

  return (
    <section className="flex flex-col gap-6">
      <PageHeader
        title="Discord"
        actions={
          <Button asChild variant="secondary">
            <Link to={`/leagues/${slug}`}>Back to the league</Link>
          </Button>
        }
      >
        Post league updates to a Discord channel. Nobody gets pinged.
      </PageHeader>
      {isPending && <ListSkeleton rows={1} label="Loading Discord settings" />}
      {isError &&
        (error instanceof ApiError && (error.status === 403 || error.status === 404) ? (
          <Bento>
            <EmptyState title="Owner only">Only the league's owner can set up Discord.</EmptyState>
          </Bento>
        ) : (
          <ErrorState message="Discord settings couldn't be loaded." onRetry={() => refetch()} />
        ))}
      {data && <DiscordForm key={data.maskedUrl ?? 'new'} slug={slug} settings={data} />}
    </section>
  )
}

function DiscordForm({ slug, settings }: { slug: string; settings: DiscordSettings }) {
  const { save, remove, test } = useDiscordActions(slug)
  const [url, setUrl] = useState('')
  const [topics, setTopics] = useState<Set<DiscordTopic>>(new Set(settings.topics))

  function toggle(topic: DiscordTopic, on: boolean) {
    setTopics((current) => {
      const next = new Set(current)
      if (on) next.add(topic)
      else next.delete(topic)
      return next
    })
  }

  return (
    <Bento>
      <Tile tone="violet" className="flex flex-col gap-6 md:col-span-6 xl:col-span-7">
        <div className="flex items-center gap-4">
          <IconTile>
            <MessageSquare className="size-6" />
          </IconTile>
          <div>
            <h2 className="type-title">{settings.configured ? 'Connected channel' : 'Connect a channel'}</h2>
            {settings.configured && <p className="font-mono text-xs break-all text-tile-muted">{settings.maskedUrl}</p>}
          </div>
        </div>

        {settings.configured && !settings.enabled && (
          <p className="flex items-start gap-2 rounded-inner bg-tile-soft p-4 text-sm" role="alert">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
            Posting is switched off. {settings.lastError} Save again to switch it back on.
          </p>
        )}
        {settings.enabled && settings.lastError && settings.lastErrorAt && (
          <p className="flex items-start gap-2 rounded-inner bg-tile-soft p-4 text-sm">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
            Last post failed ({formatWhen(settings.lastErrorAt)}): {settings.lastError}
          </p>
        )}

        <form
          className="flex flex-col gap-6"
          onSubmit={(event) => {
            event.preventDefault()
            save.mutate(
              { url: url.trim() || undefined, topics: [...topics] },
              {
                onSuccess: () => {
                  toast.success('Discord settings saved')
                  setUrl('')
                },
              },
            )
          }}
        >
          <TextField
            label={settings.configured ? 'New webhook URL (leave empty to keep the current one)' : 'Webhook URL'}
            hint="In Discord: channel settings → Integrations → Webhooks → New Webhook → Copy Webhook URL."
            placeholder="https://discord.com/api/webhooks/…"
            required={!settings.configured}
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            autoComplete="off"
            spellCheck={false}
          />
          <fieldset className="flex flex-col gap-2">
            <legend className="type-label mb-2 px-1 text-tile-muted">Post these updates</legend>
            {DISCORD_TOPICS.map((t) => (
              <label key={t.key} className="flex items-center gap-3 rounded-inner bg-tile-soft px-4 py-3 text-sm">
                <input
                  type="checkbox"
                  checked={topics.has(t.key)}
                  onChange={(e) => toggle(t.key, e.target.checked)}
                  className="size-5 accent-ink"
                />
                {t.label}
              </label>
            ))}
          </fieldset>
          {save.isError && <ErrorState message={save.error.message} />}
          <div className="flex flex-wrap gap-3">
            <Button type="submit" size="lg" disabled={save.isPending || topics.size === 0}>
              {save.isPending ? 'Saving…' : 'Save'}
            </Button>
            {settings.configured && (
              <Button
                type="button"
                size="lg"
                variant="secondary"
                disabled={test.isPending}
                onClick={() => test.mutate(undefined, { onSuccess: () => toast.success('Test message sent. Check the channel.') })}
              >
                {test.isSuccess ? <Check className="size-4" aria-hidden /> : null}
                {test.isPending ? 'Sending…' : 'Send test message'}
              </Button>
            )}
          </div>
          {test.isError && <ErrorState message={test.error.message} />}
        </form>

        {settings.configured && (
          <ConfirmDialog
            trigger={
              <Button variant="ghost" className="self-start">
                Disconnect Discord
              </Button>
            }
            title="Disconnect Discord?"
            confirmLabel="Disconnect"
            onConfirm={() => remove.mutate(undefined, { onSuccess: () => toast.success('Discord disconnected') })}
          >
            The league stops posting to this channel. You can connect it again with the webhook URL.
          </ConfirmDialog>
        )}
      </Tile>
    </Bento>
  )
}
