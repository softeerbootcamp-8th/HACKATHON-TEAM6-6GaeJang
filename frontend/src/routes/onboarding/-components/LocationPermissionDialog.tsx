import { MapPin } from 'lucide-react'

import { Button } from '@/components/ui/button'

type LocationPermissionDialogProps = {
  isOpen: boolean
  onAllow: (persist: boolean) => void
  onDeny: () => void
}

/**
 * 와이어프레임(Section 1 Screen 7)에 정의된 위치 권한 수집 다이얼로그
 */
export function LocationPermissionDialog({
  isOpen,
  onAllow,
  onDeny,
}: LocationPermissionDialogProps) {
  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-6 backdrop-blur-xs">
      <div className="w-full max-w-sm overflow-hidden rounded-2xl bg-bg p-6 text-center shadow-xl">
        <div className="bg-primary/10 mx-auto mb-4 flex size-12 items-center justify-center rounded-full">
          <MapPin className="text-primary size-6" />
        </div>

        <h3 className="text-lg font-bold text-fg">
          &lsquo;Delipot&rsquo; 앱이 사용자의 위치를
          <br />
          사용하도록 허용하시겠습니까?
        </h3>

        <p className="text-muted-fg mt-2 text-xs leading-relaxed">
          우리 아파트에 진입할 때 자동으로
          <br />
          배달팟 현황 알림을 받을 수 있어요!
        </p>

        <div className="mt-6 flex flex-col gap-2">
          <Button
            type="button"
            variant="default"
            onClick={() => onAllow(true)}
            className="h-11 w-full rounded-xl text-sm font-semibold"
          >
            앱을 사용하는 동안 허용
          </Button>

          <Button
            type="button"
            variant="outline"
            onClick={() => onAllow(false)}
            className="h-11 w-full rounded-xl text-sm font-medium"
          >
            한 번 허용
          </Button>

          <Button
            type="button"
            variant="ghost"
            onClick={onDeny}
            className="text-muted-fg hover:text-fg h-10 w-full rounded-xl text-xs font-normal"
          >
            허용 안 함
          </Button>
        </div>
      </div>
    </div>
  )
}
