type AppLogoHeaderProps = {
  className?: string
  compact?: boolean
}

export function AppLogoHeader({ className = '', compact = false }: AppLogoHeaderProps) {
  const containerSize = compact
    ? 'min-h-[52px] pt-[max(env(safe-area-inset-top),1rem)] pb-2'
    : 'min-h-[78px] pt-[max(env(safe-area-inset-top),1.5rem)] pb-3'
  const logoSize = compact ? 'h-[25px] w-[71px]' : 'h-[38px] w-[106px]'

  return (
    <div className={`flex shrink-0 items-end justify-center ${containerSize} ${className}`}>
      <img
        src="/brand/delipot-logo.png"
        alt="Delipot"
        width={compact ? 71 : 106}
        height={compact ? 25 : 38}
        className={`${logoSize} object-contain`}
      />
    </div>
  )
}
