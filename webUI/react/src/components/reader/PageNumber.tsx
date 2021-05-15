import { makeStyles } from '@material-ui/core/styles';
import React from 'react';

const useStyles = (settings: IReaderSettings) => makeStyles({
    pageNumber: {
        display: settings.showPageNumber ? 'block' : 'none',
        position: 'fixed',
        bottom: '50px',
        right: settings.staticNav ? 'calc((100vw - 325px)/2)' : 'calc((100vw - 25px)/2)',
        width: '50px',
        textAlign: 'center',
        backgroundColor: 'rgba(0, 0, 0, 0.3)',
        borderRadius: '10px',
    },
});

interface IProps {
    settings: IReaderSettings
    curPage: number
    pageCount: number
}

export default function PageNumber(props: IProps) {
    const { settings, curPage, pageCount } = props;
    const classes = useStyles(settings)();

    return (
        <div className={classes.pageNumber}>
            {`${curPage + 1} / ${pageCount}`}
        </div>
    );
}
