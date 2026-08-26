type AppLogoHeaderProps = {
  className?: string
}

export function AppLogoHeader({ className = '' }: AppLogoHeaderProps) {
  return (
    <div
      className={`flex min-h-[78px] shrink-0 items-end justify-center pt-[max(env(safe-area-inset-top),1.5rem)] pb-3 ${className}`}
    >
      <img
        src="/brand/delipot-logo.png"
        alt="Delipot"
        width={106}
        height={38}
        className="h-[38px] w-[106px] object-contain"
      />
    </div>
  )
}
