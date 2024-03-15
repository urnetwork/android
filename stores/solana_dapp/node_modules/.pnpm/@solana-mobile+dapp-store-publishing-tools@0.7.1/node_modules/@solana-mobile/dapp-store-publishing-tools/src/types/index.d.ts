declare module "image-size" {
  export function imageSize(
    path: string,
    cb: (error: Error, dimensions: { width: number; height: number }) => void
  );
}
